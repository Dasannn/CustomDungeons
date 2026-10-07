execute in minecraft:cd_dungeons run forceload add 1700 594 1796 628
execute in minecraft:cd_dungeons run fill 1700 64 600 1796 64 628 mossy_stone_bricks
execute in minecraft:cd_dungeons run fill 1700 64 600 1710 70 628 mossy_stone_bricks hollow
execute in minecraft:cd_dungeons run fill 1700 71 600 1710 71 628 chiseled_stone_bricks
execute in minecraft:cd_dungeons run fill 1700 65 613 1700 68 615 air
execute in minecraft:cd_dungeons run fill 1710 65 613 1710 68 615 chiseled_stone_bricks
execute in minecraft:cd_dungeons run fill 1711 64 601 1734 64 627 mossy_stone_bricks
execute in minecraft:cd_dungeons run fill 1710 65 601 1710 77 627 mossy_stone_bricks
execute in minecraft:cd_dungeons run fill 1734 65 601 1734 77 627 mossy_stone_bricks
execute in minecraft:cd_dungeons run fill 1711 65 601 1734 77 601 mossy_stone_bricks
execute in minecraft:cd_dungeons run fill 1711 65 627 1734 77 627 mossy_stone_bricks
execute in minecraft:cd_dungeons run fill 1711 77 602 1734 77 626 chiseled_stone_bricks
execute in minecraft:cd_dungeons run fill 1711 64 613 1734 64 615 oxidized_copper
execute in minecraft:cd_dungeons run fill 1714 65 604 1714 75 604 chiseled_stone_bricks
execute in minecraft:cd_dungeons run setblock 1714 70 603 sea_lantern
execute in minecraft:cd_dungeons run fill 1714 65 624 1714 75 624 chiseled_stone_bricks
execute in minecraft:cd_dungeons run setblock 1714 70 623 sea_lantern
execute in minecraft:cd_dungeons run fill 1731 65 604 1731 75 604 chiseled_stone_bricks
execute in minecraft:cd_dungeons run setblock 1731 70 603 sea_lantern
execute in minecraft:cd_dungeons run fill 1731 65 624 1731 75 624 chiseled_stone_bricks
execute in minecraft:cd_dungeons run setblock 1731 70 623 sea_lantern
execute in minecraft:cd_dungeons run fill 1710 65 613 1710 68 615 chiseled_stone_bricks
execute in minecraft:cd_dungeons run fill 1734 65 613 1734 68 615 chiseled_stone_bricks
execute in minecraft:cd_dungeons run fill 1718 65 605 1718 68 626 mossy_stone_bricks
execute in minecraft:cd_dungeons run fill 1725 65 602 1725 68 623 mossy_stone_bricks
execute in minecraft:cd_dungeons run fill 1735 64 601 1758 64 627 mossy_stone_bricks
execute in minecraft:cd_dungeons run fill 1734 65 601 1734 77 627 mossy_stone_bricks
execute in minecraft:cd_dungeons run fill 1758 65 601 1758 77 627 mossy_stone_bricks
execute in minecraft:cd_dungeons run fill 1735 65 601 1758 77 601 mossy_stone_bricks
execute in minecraft:cd_dungeons run fill 1735 65 627 1758 77 627 mossy_stone_bricks
execute in minecraft:cd_dungeons run fill 1735 77 602 1758 77 626 chiseled_stone_bricks
execute in minecraft:cd_dungeons run fill 1735 64 613 1758 64 615 oxidized_copper
execute in minecraft:cd_dungeons run fill 1738 65 604 1738 75 604 chiseled_stone_bricks
execute in minecraft:cd_dungeons run setblock 1738 70 603 sea_lantern
execute in minecraft:cd_dungeons run fill 1738 65 624 1738 75 624 chiseled_stone_bricks
execute in minecraft:cd_dungeons run setblock 1738 70 623 sea_lantern
execute in minecraft:cd_dungeons run fill 1755 65 604 1755 75 604 chiseled_stone_bricks
execute in minecraft:cd_dungeons run setblock 1755 70 603 sea_lantern
execute in minecraft:cd_dungeons run fill 1755 65 624 1755 75 624 chiseled_stone_bricks
execute in minecraft:cd_dungeons run setblock 1755 70 623 sea_lantern
execute in minecraft:cd_dungeons run fill 1734 65 613 1734 68 615 chiseled_stone_bricks
execute in minecraft:cd_dungeons run fill 1758 65 613 1758 68 615 chiseled_stone_bricks
execute in minecraft:cd_dungeons run fill 1759 64 601 1782 64 627 mossy_stone_bricks
execute in minecraft:cd_dungeons run fill 1758 65 601 1758 77 627 mossy_stone_bricks
execute in minecraft:cd_dungeons run fill 1782 65 601 1782 77 627 mossy_stone_bricks
execute in minecraft:cd_dungeons run fill 1759 65 601 1782 77 601 mossy_stone_bricks
execute in minecraft:cd_dungeons run fill 1759 65 627 1782 77 627 mossy_stone_bricks
execute in minecraft:cd_dungeons run fill 1759 77 602 1782 77 626 chiseled_stone_bricks
execute in minecraft:cd_dungeons run fill 1759 64 613 1782 64 615 oxidized_copper
execute in minecraft:cd_dungeons run fill 1762 65 604 1762 75 604 chiseled_stone_bricks
execute in minecraft:cd_dungeons run setblock 1762 70 603 sea_lantern
execute in minecraft:cd_dungeons run fill 1762 65 624 1762 75 624 chiseled_stone_bricks
execute in minecraft:cd_dungeons run setblock 1762 70 623 sea_lantern
execute in minecraft:cd_dungeons run fill 1779 65 604 1779 75 604 chiseled_stone_bricks
execute in minecraft:cd_dungeons run setblock 1779 70 603 sea_lantern
execute in minecraft:cd_dungeons run fill 1779 65 624 1779 75 624 chiseled_stone_bricks
execute in minecraft:cd_dungeons run setblock 1779 70 623 sea_lantern
execute in minecraft:cd_dungeons run fill 1758 65 613 1758 68 615 chiseled_stone_bricks
execute in minecraft:cd_dungeons run fill 1782 65 613 1782 68 615 air
execute in minecraft:cd_dungeons run fill 1766 65 605 1766 68 626 mossy_stone_bricks
execute in minecraft:cd_dungeons run fill 1773 65 602 1773 68 623 mossy_stone_bricks
scoreboard objectives add le47 dummy
scoreboard players set #state le47 0
scoreboard players set #wrong le47 0
execute in minecraft:cd_dungeons run setblock 1740 67 601 gold_block
execute in minecraft:cd_dungeons run setblock 1740 66 601 command_block[facing=north,conditional=false]{Command:"execute unless score #state le47 matches 0 run scoreboard players set #wrong le47 1",auto:0b,TrackOutput:0b}
execute in minecraft:cd_dungeons run setblock 1740 66 600 chain_command_block[facing=north,conditional=false]{Command:"execute if score #state le47 matches 0 run scoreboard players set #state le47 1",auto:1b,TrackOutput:0b}
execute in minecraft:cd_dungeons run setblock 1740 66 599 chain_command_block[facing=north,conditional=false]{Command:"execute if score #wrong le47 matches 1 run scoreboard players set #state le47 0",auto:1b,TrackOutput:0b}
execute in minecraft:cd_dungeons run setblock 1740 66 598 chain_command_block[facing=north,conditional=false]{Command:"scoreboard players set #wrong le47 0",auto:1b,TrackOutput:0b}
execute in minecraft:cd_dungeons run setblock 1740 66 602 stone_button[face=wall,facing=south]
execute in minecraft:cd_dungeons run setblock 1744 67 601 copper_block
execute in minecraft:cd_dungeons run setblock 1744 66 601 command_block[facing=north,conditional=false]{Command:"execute unless score #state le47 matches 1 run scoreboard players set #wrong le47 1",auto:0b,TrackOutput:0b}
execute in minecraft:cd_dungeons run setblock 1744 66 600 chain_command_block[facing=north,conditional=false]{Command:"execute if score #state le47 matches 1 run scoreboard players set #state le47 2",auto:1b,TrackOutput:0b}
execute in minecraft:cd_dungeons run setblock 1744 66 599 chain_command_block[facing=north,conditional=false]{Command:"execute if score #wrong le47 matches 1 run scoreboard players set #state le47 0",auto:1b,TrackOutput:0b}
execute in minecraft:cd_dungeons run setblock 1744 66 598 chain_command_block[facing=north,conditional=false]{Command:"scoreboard players set #wrong le47 0",auto:1b,TrackOutput:0b}
execute in minecraft:cd_dungeons run setblock 1744 66 602 stone_button[face=wall,facing=south]
execute in minecraft:cd_dungeons run setblock 1748 67 601 amethyst_block
execute in minecraft:cd_dungeons run setblock 1748 66 601 command_block[facing=north,conditional=false]{Command:"execute unless score #state le47 matches 2 run scoreboard players set #wrong le47 1",auto:0b,TrackOutput:0b}
execute in minecraft:cd_dungeons run setblock 1748 66 600 chain_command_block[facing=north,conditional=false]{Command:"execute if score #state le47 matches 2 run scoreboard players set #state le47 3",auto:1b,TrackOutput:0b}
execute in minecraft:cd_dungeons run setblock 1748 66 599 chain_command_block[facing=north,conditional=false]{Command:"execute if score #wrong le47 matches 1 run scoreboard players set #state le47 0",auto:1b,TrackOutput:0b}
execute in minecraft:cd_dungeons run setblock 1748 66 598 chain_command_block[facing=north,conditional=false]{Command:"scoreboard players set #wrong le47 0",auto:1b,TrackOutput:0b}
execute in minecraft:cd_dungeons run setblock 1748 66 597 chain_command_block[facing=north,conditional=false]{Command:"execute if score #state le47 matches 3 run customdungeon key give @p laberinto-enigma",auto:1b,TrackOutput:0b}
execute in minecraft:cd_dungeons run setblock 1748 66 596 chain_command_block[facing=north,conditional=false]{Command:"scoreboard players set #state le47 0",auto:1b,TrackOutput:0b}
execute in minecraft:cd_dungeons run setblock 1748 66 602 stone_button[face=wall,facing=south]
execute in minecraft:cd_dungeons run forceload remove 1700 594 1796 628
