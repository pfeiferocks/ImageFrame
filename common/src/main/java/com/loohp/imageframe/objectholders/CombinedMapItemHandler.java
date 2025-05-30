/*
 * This file is part of ImageFrame.
 *
 * Copyright (C) 2025. LoohpJames <jamesloohp@gmail.com>
 * Copyright (C) 2025. Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.loohp.imageframe.objectholders;

import com.loohp.imageframe.ImageFrame;
import com.loohp.imageframe.nms.NMS;
import com.loohp.imageframe.utils.ItemFrameUtils;
import com.loohp.imageframe.utils.PlayerUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class CombinedMapItemHandler implements Listener, AutoCloseable {

    private final Set<Player> entityInteractionChecking;
    private final Set<Player> entityDamageChecking;

    public CombinedMapItemHandler() {
        this.entityInteractionChecking = new HashSet<>();
        this.entityDamageChecking = new HashSet<>();
        Bukkit.getPluginManager().registerEvents(this, ImageFrame.plugin);
    }

    public ItemStack getCombinedMap(ImageMap imageMap) {
        ItemStack itemStack = new ItemStack(Material.PAPER);
        ItemMeta meta = itemStack.getItemMeta();
        meta.setDisplayName(ImageFrame.combinedMapItemNameFormat
                .replace("{ImageID}", imageMap.getImageIndex() + "")
                .replace("{Name}", imageMap.getName())
                .replace("{Width}", imageMap.getWidth() + "")
                .replace("{Height}", imageMap.getHeight() + "")
                .replace("{DitheringType}", imageMap.getDitheringType().getName())
                .replace("{CreatorName}", imageMap.getCreatorName())
                .replace("{CreatorUUID}", imageMap.getCreator().toString())
                .replace("{TimeCreated}", ImageFrame.dateFormat.format(new Date(imageMap.getCreationTime()))));
        meta.setLore(ImageFrame.combinedMapItemLoreFormat.stream().map(each -> each
                .replace("{ImageID}", imageMap.getImageIndex() + "")
                .replace("{Name}", imageMap.getName())
                .replace("{Width}", imageMap.getWidth() + "")
                .replace("{Height}", imageMap.getHeight() + "")
                .replace("{DitheringType}", imageMap.getDitheringType().getName())
                .replace("{CreatorName}", imageMap.getCreatorName())
                .replace("{CreatorUUID}", imageMap.getCreator().toString())
                .replace("{TimeCreated}", ImageFrame.dateFormat.format(new Date(imageMap.getCreationTime())))).collect(Collectors.toList()));
        meta.setCustomModelData(imageMap.getImageIndex());
        itemStack.setItemMeta(meta);
        return NMS.getInstance().withCombinedMapItemInfo(itemStack, new CombinedMapItemInfo(imageMap.getImageIndex()));
    }

    public void giveCombinedMap(ImageMap imageMap, Player player) {
        giveCombinedMap(imageMap, Collections.singleton(player));
    }

    public void giveCombinedMap(ImageMap imageMap, Collection<? extends Player> players) {
        ItemStack map = getCombinedMap(imageMap);
        for (Player player : players) {
            PlayerUtils.giveItem(player, map.clone());
        }
    }

    @Override
    public void close() {
        HandlerList.unregisterAll(this);
    }

    public ItemFrameSelectionManager.SelectedItemFrameResult findItemFrames(ItemFrame itemFrame, float yaw, int width, int height, Predicate<ItemStack> itemCheck) {
        Vector up;
        Vector left;
        BlockFace facing = itemFrame.getFacing();
        if (ItemFrameUtils.isOnWalls(itemFrame)) {
            up = new Vector(0, 1, 0);
            left = facing.getDirection().rotateAroundY(Math.toRadians(-90));
        } else if (ItemFrameUtils.isOnCeiling(itemFrame)) {
            up = ItemFrameUtils.getClosestCardinalDirection(yaw + 180F);
            left = up.clone().rotateAroundY(Math.toRadians(-90));
        } else {
            up = ItemFrameUtils.getClosestCardinalDirection(yaw);
            left = up.clone().rotateAroundY(Math.toRadians(90));
        }
        List<Location> checkingLocations = getCheckingLocations(itemFrame.getLocation(), up, left, width, height);
        outer: for (Location location : checkingLocations) {
            itemFrame = getItemFrame(location, facing);
            if (itemFrame == null) {
                continue;
            }
            ItemFrame opposite = getOppositeItemFrameCorner(itemFrame, up, left, width, height);
            if (opposite == null) {
                continue;
            }
            ItemFrameSelectionManager.SelectedItemFrameResult result = ImageFrame.itemFrameSelectionManager.getSelectedItemFrames(yaw, itemFrame, opposite);
            if (result == null) {
                continue;
            }
            List<ItemFrame> selectedFrames = result.getItemFrames();
            if (selectedFrames.size() != width * height) {
                continue;
            }
            for (ItemFrame frame : selectedFrames) {
                ItemStack item = frame.getItem();
                if (!itemCheck.test(item)) {
                    continue outer;
                }
            }
            return result;
        }
        return null;
    }
    
    public ItemFrame getOppositeItemFrameCorner(ItemFrame itemFrame, Vector up, Vector left, int width, int height) {
        Location location = itemFrame.getLocation().add(left.clone().multiply(-(width - 1))).add(up.clone().multiply(-(height - 1)));
        BoundingBox boundingBox = BoundingBox.of(location.getBlock(), location.getBlock());
        Collection<Entity> entities = location.getWorld().getNearbyEntities(boundingBox, e -> e instanceof ItemFrame);
        for (Entity entity : entities) {
            if (itemFrame.getFacing().equals(entity.getFacing())) {
                return (ItemFrame) entity;
            }
        }
        return null;
    }

    public ItemFrame getItemFrame(Location location, BlockFace facing) {
        BoundingBox boundingBox = BoundingBox.of(location.getBlock(), location.getBlock());
        Collection<Entity> entities = location.getWorld().getNearbyEntities(boundingBox, e -> e instanceof ItemFrame);
        for (Entity entity : entities) {
            if (facing.equals(entity.getFacing())) {
                return (ItemFrame) entity;
            }
        }
        return null;
    }

    public List<Location> getCheckingLocations(Location origin, Vector up, Vector left, int width, int height) {
        List<Location> locations = new ArrayList<>(width * height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                locations.add(origin.clone().add(up.clone().multiply(y)).add(left.clone().multiply(x)));
            }
        }
        locations.sort(Comparator.comparing(each -> each.distanceSquared(origin)));
        return locations;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (!entityInteractionChecking.add(player)) {
            return;
        }
        try {
            EquipmentSlot slot = event.getHand();
            ItemStack itemStack = player.getEquipment().getItem(slot);
            if (itemStack == null || !itemStack.getType().equals(Material.PAPER)) {
                return;
            }
            CombinedMapItemInfo mapItemInfo = NMS.getInstance().getCombinedMapItemInfo(itemStack);
            if (mapItemInfo == null) {
                return;
            }
            Entity entity = event.getRightClicked();
            if (!(entity instanceof ItemFrame)) {
                event.setCancelled(true);
                return;
            }
            ItemFrame itemFrame = (ItemFrame) entity;
            int id = mapItemInfo.getImageMapIndex();
            ImageMap imageMap = ImageFrame.imageMapManager.getFromImageId(id);
            if (imageMap == null) {
                player.sendMessage(ImageFrame.messageInvalidImageMap);
                event.setCancelled(true);
                return;
            }
            float yaw = player.getLocation().getYaw();
            ItemFrameSelectionManager.SelectedItemFrameResult selection = findItemFrames(itemFrame, yaw, imageMap.getWidth(), imageMap.getHeight(), item -> item == null || item.getType().equals(Material.AIR));
            if (selection == null) {
                event.setCancelled(true);
                player.sendMessage(ImageFrame.messageNotEnoughSpace);
                return;
            }
            List<ItemFrame> selectedFrames = selection.getItemFrames();
            for (ItemFrame frame : selectedFrames) {
                ItemStack item = frame.getItem();
                if ((item != null && !item.getType().equals(Material.AIR)) || !PlayerUtils.isInteractionAllowed(player, frame)) {
                    event.setCancelled(true);
                    player.sendMessage(ImageFrame.messageItemFrameOccupied);
                    return;
                }
            }
            UUID uuid = UUID.randomUUID();
            imageMap.fillItemFrames(selectedFrames, selection.getRotation(), (frame, item) -> true, (frame, item) -> {}, ImageFrame.mapItemFormat, item -> {
                CombinedMapItemInfo newInfo = new CombinedMapItemInfo(imageMap.getImageIndex(), new CombinedMapItemInfo.PlacementInfo(yaw, uuid));
                return NMS.getInstance().withCombinedMapItemInfo(item, newInfo);
            });
        } finally {
            entityInteractionChecking.remove(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamageHanging(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof ItemFrame)) {
            return;
        }
        ItemFrame itemFrame = (ItemFrame) entity;
        Player player = null;
        if (event instanceof EntityDamageByEntityEvent) {
            EntityDamageByEntityEvent entityDamageByEntityEvent = (EntityDamageByEntityEvent) event;
            Entity damager = entityDamageByEntityEvent.getDamager();
            if (damager instanceof Player) {
                player = (Player) damager;
            }
        }
        handleItemFrameBreak(player, itemFrame);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRemoveHanging(HangingBreakEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof ItemFrame)) {
            return;
        }
        ItemFrame itemFrame = (ItemFrame) entity;
        handleItemFrameBreak(null, itemFrame);
    }

    public void handleItemFrameBreak(Player player, ItemFrame itemFrame) {
        if (player != null && !entityDamageChecking.add(player)) {
            return;
        }
        try {
            ItemStack itemStack = itemFrame.getItem();
            if (itemStack == null || !itemStack.getType().equals(Material.FILLED_MAP)) {
                return;
            }
            CombinedMapItemInfo mapItemInfo = NMS.getInstance().getCombinedMapItemInfo(itemStack);
            if (mapItemInfo == null || !mapItemInfo.hasPlacement()) {
                return;
            }
            CombinedMapItemInfo.PlacementInfo placement = mapItemInfo.getPlacement();
            int id = mapItemInfo.getImageMapIndex();
            float yaw = placement.getYaw();
            UUID uuid = placement.getUniqueId();
            ImageMap imageMap = ImageFrame.imageMapManager.getFromImageId(id);
            if (imageMap == null) {
                return;
            }
            ItemFrameSelectionManager.SelectedItemFrameResult selection = findItemFrames(itemFrame, yaw, imageMap.getWidth(), imageMap.getHeight(), item -> {
                if (item == null || !item.getType().equals(Material.FILLED_MAP)) {
                    return false;
                }
                CombinedMapItemInfo info = NMS.getInstance().getCombinedMapItemInfo(item);
                if (info == null || !info.hasPlacement()) {
                    return false;
                }
                return info.getImageMapIndex() == id && info.getPlacement().getUniqueId().equals(uuid);
            });
            if (selection == null) {
                return;
            }
            List<ItemFrame> itemFrames = selection.getItemFrames();
            if (player == null || itemFrames.stream().allMatch(each -> PlayerUtils.isDamageAllowed(player, each))) {
                itemFrames.forEach(each -> each.setItem(null, false));
                itemFrame.setItem(getCombinedMap(imageMap), false);
            } else {
                player.sendMessage(ImageFrame.messageItemFrameOccupied);
            }
        } finally {
            entityDamageChecking.remove(player);
        }
    }

}
