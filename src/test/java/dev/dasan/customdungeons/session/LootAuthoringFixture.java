package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.model.*;
import java.util.concurrent.CompletableFuture;

/** Real T63 persistence/block fixture shared with GUI lifecycle regressions. */
public final class LootAuthoringFixture {
    private final LootChestServiceTest fixture=new LootChestServiceTest();
    public LootChestService service() {return fixture.loot;}
    public void place(String dungeon,String room,LootChestDef chest) {
        var receipt=fixture.loot.place(dungeon,room,chest);tick();receipt.join();
    }
    public void tick() {fixture.loot.tick();}
    public CompletableFuture<Void> delayNextWrite() {var write=new CompletableFuture<Void>();fixture.store.next=write;return write;}
    public boolean blocked(String dungeon) {return fixture.loot.blocked(dungeon);}
    public String block() {return fixture.blocks.data;}
    public String original() {return LootChestServiceTest.ORIGINAL;}
}
