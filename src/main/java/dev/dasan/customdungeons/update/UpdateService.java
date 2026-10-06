package dev.dasan.customdungeons.update;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.dasan.customdungeons.CustomDungeonsPlugin;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.ServicePriority;

/** One in-flight operation, explicit per-sender confirmation, and a fail-closed staging boundary. */
public final class UpdateService implements AutoCloseable {
    public static final int MAX_JAR_BYTES = 20 * 1024 * 1024;
    private static final int MAX_METADATA_BYTES = 1024 * 1024;
    private static final long CONFIRM_MILLIS = 60_000;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    public record Settings(boolean enabled, String repository, URI apiBase) {}
    public record Result(String key, String version, URI notes) {
        static Result of(String key) { return new Result(key, "", null); }
    }
    private record Asset(URI uri, int size) {}
    private record Release(SemVer version, URI notes, Asset jar, Asset signature) {
        Result result(String key) { return new Result(key, version.toString(), notes); }
    }
    private record Pending(Release release, Settings settings, long created) {}
    @FunctionalInterface interface Transport { byte[] get(URI uri, int maximum) throws Exception; }
    @FunctionalInterface private interface Operation { Result run() throws Exception; }
    private static final class Failure extends IOException {
        final String key;
        Failure(String key) { super(key); this.key = key; }
    }
    private final SemVer current;
    private final Path temporaryDirectory;
    private final Path destination;
    private final SignatureVerifier verifier;
    private final LongSupplier clock;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Transport transport;
    private final AtomicBoolean busy = new AtomicBoolean();
    // Only the worker accesses pending; close and final move share the lifecycle lock.
    private final Map<String, Pending> pending = new HashMap<>();
    private final Object lifecycle = new Object();
    private volatile boolean closed;
    private volatile HttpClient client;

    private UpdateService(String current, Path temporaryDirectory, Path destination) {
        this(current, temporaryDirectory, destination, new SignatureVerifier(), null,
                () -> System.nanoTime() / 1_000_000);
    }
    UpdateService(String current, Path temporaryDirectory, Path destination, SignatureVerifier verifier,
                  Transport transport, LongSupplier clock) {
        this.current = SemVer.parse(current);
        this.temporaryDirectory = temporaryDirectory;
        this.destination = destination;
        this.verifier = verifier;
        this.clock = clock;
        this.transport = transport == null ? this::httpGet : transport;
    }
    public static void register(CustomDungeonsPlugin plugin) {
        try {
            Path loaded = Path.of(plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
            String filename = loaded.getFileName().toString();
            if (!filename.endsWith(".jar")) throw new IllegalStateException("Plugin was not loaded from a jar");
            var service = new UpdateService(plugin.getPluginMeta().getVersion(),
                    plugin.getDataFolder().toPath().resolve("update-tmp"), Bukkit.getUpdateFolderFile().toPath().resolve(filename));
            plugin.getServer().getServicesManager().register(UpdateService.class, service, plugin, ServicePriority.Normal);
            plugin.getServer().getPluginManager().registerEvents(new Listener() {
                @EventHandler public void disable(PluginDisableEvent event) {
                    if (event.getPlugin() == plugin) service.close();
                }
            }, plugin);
        } catch (Exception error) { throw new IllegalStateException("Cannot initialize updater", error); }
    }
    public CompletableFuture<Result> check(Settings settings) {
        return submit(settings, () -> {
            Release release = latest(settings, false);
            return release.result(release.version.compareTo(current) > 0 ? "update.available" : "update.up-to-date");
        });
    }
    public CompletableFuture<Result> prepare(String sender, Settings settings) {
        return submit(settings, () -> {
            pending.remove(sender);
            Release release = latest(settings, true);
            if (release.version.compareTo(current) <= 0) return release.result("update.up-to-date");
            pending.put(sender, new Pending(release, settings, clock.getAsLong()));
            return release.result("update.confirm-required");
        });
    }
    public CompletableFuture<Result> confirm(String sender, Settings settings) {
        return submit(settings, () -> {
            Pending confirmation = pending.remove(sender);
            if (confirmation == null || clock.getAsLong() - confirmation.created > CONFIRM_MILLIS
                    || !confirmation.settings.equals(settings)) return Result.of("update.expired");
            Release release = confirmation.release;
            if (release.version.compareTo(current) <= 0) return release.result("update.up-to-date");
            return stage(release);
        });
    }
    private CompletableFuture<Result> submit(Settings settings, Operation action) {
        if (closed || !settings.enabled) return CompletableFuture.completedFuture(Result.of("update.disabled"));
        if (!busy.compareAndSet(false, true)) return CompletableFuture.completedFuture(Result.of("update.busy"));
        try {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    if (closed) return Result.of("update.disabled");
                    pending.entrySet().removeIf(entry -> clock.getAsLong() - entry.getValue().created > CONFIRM_MILLIS);
                    return action.run();
                } catch (Failure error) { return Result.of(error.key); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); return Result.of("update.disabled"); }
                catch (Exception error) { return Result.of("update.download-failed"); }
                finally { busy.set(false); }
            }, executor);
        } catch (java.util.concurrent.RejectedExecutionException error) {
            busy.set(false); return CompletableFuture.completedFuture(Result.of("update.disabled"));
        }
    }
    private Release latest(Settings settings, boolean requireAssets) throws Exception {
        https(settings.apiBase);
        if (settings.apiBase.getQuery() != null || !settings.apiBase.getPath().endsWith("/")
                || settings.repository == null || !settings.repository.matches("[A-Za-z0-9_-]+/[A-Za-z0-9_.-]+")
                || settings.repository.endsWith("/.") || settings.repository.endsWith("/.."))
            throw new Failure("update.invalid-release");
        byte[] bytes = fetch(settings.apiBase.resolve("repos/" + settings.repository + "/releases/latest"), MAX_METADATA_BYTES);
        try {
            JsonObject json = JsonParser.parseString(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            String tag = string(json, "tag_name");
            SemVer version = SemVer.parse(tag.startsWith("v") ? tag.substring(1) : tag);
            URI notes = https(URI.create(string(json, "html_url")));
            // Up-to-date checks must not fetch assets (or require valid downloadable assets).
            if (!requireAssets || version.compareTo(current) <= 0) return new Release(version, notes, null, null);
            String filename = "CustomDungeons-" + version + ".jar";
            Asset jar = null, signature = null;
            for (var element : json.getAsJsonArray("assets")) {
                var asset = element.getAsJsonObject();
                String name = string(asset, "name");
                if (!name.equals(filename) && !name.equals(filename + ".sig")) continue;
                long size = asset.get("size").getAsBigDecimal().longValueExact();
                boolean isJar = name.equals(filename);
                if (size <= 0 || size > (isJar ? MAX_JAR_BYTES : 64)) throw new Failure("update.too-large");
                if (!isJar && size != 64) throw new Failure("update.invalid-signature");
                var selected = new Asset(https(URI.create(string(asset, "browser_download_url"))), (int) size);
                if (isJar) { if (jar != null) throw new Failure("update.invalid-release"); jar = selected; }
                else { if (signature != null) throw new Failure("update.invalid-release"); signature = selected; }
            }
            if (jar == null || signature == null) throw new Failure("update.invalid-release");
            return new Release(version, notes, jar, signature);
        } catch (Failure error) { throw error; }
        catch (RuntimeException error) { throw new Failure("update.invalid-release"); }
    }
    private static String string(JsonObject object, String key) {
        var value = Objects.requireNonNull(object.get(key));
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Expected string");
        return value.getAsString();
    }
    private Result stage(Release release) throws Exception {
        Path temporary = null;
        try {
            if (Files.isSymbolicLink(temporaryDirectory)) throw new Failure("update.io-failed");
            Files.createDirectories(temporaryDirectory);
            temporary = Files.createTempFile(temporaryDirectory, "release-", ".jar");
            byte[] jar = fetch(release.jar.uri, MAX_JAR_BYTES);
            if (jar.length != release.jar.size) throw new Failure("update.invalid-release");
            Files.write(temporary, jar);
            byte[] signature = fetch(release.signature.uri, 64);
            if (!verifier.verify(temporary, signature)) throw new Failure("update.invalid-signature");
            try { JarInspector.verify(temporary, release.version.toString()); }
            catch (IOException error) { throw new Failure("update.invalid-jar"); }
            synchronized (lifecycle) {
                if (closed || Thread.currentThread().isInterrupted()) throw new Failure("update.disabled");
                Path update = destination.getParent();
                if (Files.isSymbolicLink(update) || Files.isSymbolicLink(destination)) throw new Failure("update.io-failed");
                Files.createDirectories(update);
                // No fallback copy: cross-filesystem moves fail closed, without installing anything.
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
            return release.result("update.staged");
        } catch (Failure error) { throw error; }
        catch (IOException error) { throw new Failure("update.io-failed"); }
        finally { if (temporary != null) Files.deleteIfExists(temporary); }
    }
    private byte[] fetch(URI uri, int maximum) throws Exception {
        https(uri);
        if (closed || Thread.currentThread().isInterrupted()) throw new Failure("update.disabled");
        byte[] result;
        try { result = transport.get(uri, maximum); }
        catch (Failure error) { throw error; }
        catch (IOException error) {
            // HttpClient wraps subscriber errors in IOException.
            for (Throwable cause = error; cause != null; cause = cause.getCause())
                if (cause instanceof Failure failure) throw failure;
            throw new Failure("update.download-failed");
        }
        if (result.length > maximum) throw new Failure("update.too-large");
        return result;
    }
    private static URI https(URI uri) throws Failure {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getFragment() != null) throw new Failure("update.insecure-url");
        return uri;
    }
    private byte[] httpGet(URI uri, int maximum) throws Exception {
        if (client == null) {
            synchronized (lifecycle) {
                if (closed) throw new Failure("update.disabled");
                client = HttpClient.newBuilder().executor(executor).connectTimeout(TIMEOUT)
                        .followRedirects(HttpClient.Redirect.NEVER).build();
            }
        }
        // GitHub assets redirect to release-assets; every hop must remain HTTPS.
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        for (int redirects = 0; redirects <= 5; redirects++) {
            https(uri);
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) throw new Failure("update.download-failed");
            var request = HttpRequest.newBuilder(uri).timeout(Duration.ofNanos(remaining))
                    .header("User-Agent", "CustomDungeons/" + current)
                    .header("Accept", "application/vnd.github+json, application/octet-stream").GET().build();
            var responseFuture = client.sendAsync(request, info -> {
                if (info.statusCode() != 200)
                    return HttpResponse.BodySubscribers.replacing(new byte[0]);
                var subscriber = new LimitedBody(maximum);
                long advertised = info.headers().firstValueAsLong("Content-Length").orElse(-1);
                if (advertised > maximum) subscriber.fail(new Failure("update.too-large"));
                return subscriber;
            });
            HttpResponse<byte[]> response;
            try {
                long bodyRemaining = deadline - System.nanoTime();
                if (bodyRemaining <= 0) throw new java.util.concurrent.TimeoutException();
                response = responseFuture.get(bodyRemaining, java.util.concurrent.TimeUnit.NANOSECONDS);
            } catch (java.util.concurrent.TimeoutException error) {
                responseFuture.cancel(true);
                throw new Failure("update.download-failed");
            } catch (InterruptedException error) {
                responseFuture.cancel(true); throw error;
            } catch (java.util.concurrent.ExecutionException error) {
                for (Throwable cause = error; cause != null; cause = cause.getCause())
                    if (cause instanceof Failure failure) throw failure;
                throw new Failure("update.download-failed");
            }
            if (response.statusCode() == 200) return response.body();
            if (response.statusCode() < 300 || response.statusCode() >= 400) throw new Failure("update.download-failed");
            String location = response.headers().firstValue("Location").orElseThrow(() -> new Failure("update.download-failed"));
            uri = https(uri.resolve(location));
        }
        throw new Failure("update.download-failed");
    }
    /** Bounded body subscriber also covers chunked responses, before allocating beyond the cap. */
    static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final int maximum;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private Flow.Subscription subscription;
        LimitedBody(int maximum) { this.maximum = maximum; }
        @Override public CompletionStage<byte[]> getBody() { return body; }
        @Override public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            if (body.isDone()) subscription.cancel(); else subscription.request(1);
        }
        @Override public void onNext(List<ByteBuffer> buffers) {
            if (body.isDone()) return;
            for (var buffer : buffers) {
                if (buffer.remaining() > maximum - bytes.size()) { fail(new Failure("update.too-large")); return; }
                byte[] chunk = new byte[buffer.remaining()]; buffer.get(chunk); bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable error) { fail(error); }
        @Override public void onComplete() { body.complete(bytes.toByteArray()); }
        void fail(Throwable error) { body.completeExceptionally(error); if (subscription != null) subscription.cancel(); }
    }
    @Override public void close() {
        synchronized (lifecycle) { closed = true; if (client != null) client.shutdownNow(); }
        executor.shutdownNow();
    }
}
