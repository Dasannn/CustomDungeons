# SOLO agentes, cd_dungeons. Ejecutar una línea por consola; esperar a los chunks tras forceload.
execute in minecraft:cd_dungeons run forceload add 1200 0 1290 10
execute in minecraft:cd_dungeons run fill 1200 64 0 1290 64 10 stone
execute in minecraft:cd_dungeons run fill 1200 65 -1 1251 68 -1 glass
execute in minecraft:cd_dungeons run fill 1200 65 11 1251 68 11 glass
execute in minecraft:cd_dungeons run setblock 1202 65 5 stone_pressure_plate
execute in minecraft:cd_dungeons run setblock 1204 65 5 stone_pressure_plate
execute in minecraft:cd_dungeons run setblock 1249 65 5 polished_blackstone_pressure_plate
# Laboratorio del asistente, separado del recorrido de dos salas.
execute in minecraft:cd_dungeons run forceload add 1300 0 1345 10
execute in minecraft:cd_dungeons run fill 1300 64 0 1345 64 10 stone
execute in minecraft:cd_dungeons run setblock 1330 85 10 stone
execute in minecraft:cd_dungeons run setblock 1325 80 9 stone
