package net.dmulloy2.swornguns.gun;

import java.util.ArrayList;
import java.util.List;

import net.dmulloy2.swornguns.SwornGuns;
import net.dmulloy2.swornguns.util.InventoryHelper;
import net.dmulloy2.swornguns.util.PermissionInterface;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class GunPlayer {
	private int ticks;
	private Player controller;
	private ItemStack lastHeldItem;
	private List<Gun> guns;
	private Gun currentlyFiring;
	public boolean enabled = true;
	
	private final SwornGuns plugin;
	
	public GunPlayer(final SwornGuns plugin, Player player) {
		this.plugin = plugin;
		this.controller = player;
		this.guns = plugin.getLoadedGuns();
		
		for (Gun gun : guns) {
			gun.owner = this;
		}
	}

	public boolean isAimedIn() {
		return (controller != null && controller.isOnline() && controller.hasPotionEffect(PotionEffectType.SLOW));
	}

	public boolean onClick(String clickType) {
		if (!this.enabled) {
			return false;
		}
		
		Gun holding = null;
		ItemStack hand = this.controller.getItemInHand();
		if (hand != null) {
			List<Gun> tempgun = getGunsByType(hand);
			List<Gun> canFire = new ArrayList<Gun>();
			for (int i = 0; i < tempgun.size(); i++) {
				if ((PermissionInterface.checkPermission(this.controller, ((Gun)tempgun.get(i)).node)) || (!((Gun)tempgun.get(i)).needsPermission)) {
					canFire.add((Gun)tempgun.get(i));
				}
			}
			if ((tempgun.size() > canFire.size()) && (canFire.size() == 0)) {
				if ((((Gun)tempgun.get(0)).permissionMessage != null) && (((Gun)tempgun.get(0)).permissionMessage.length() > 0))
					this.controller.sendMessage(((Gun)tempgun.get(0)).permissionMessage);
				return false;
			}
			tempgun.clear();
			for (int i = 0; i < canFire.size(); i++) {
				Gun check = (Gun)canFire.get(i);
				byte gunDat = check.getGunTypeByte();
				byte itmDat = hand.getData().getData();
				
				if ((gunDat == itmDat) || (check.ignoreItemData))
					holding = check;
			}
			canFire.clear();
		}
		if (holding != null) {
			if (((holding.canClickRight) || (holding.canAimRight())) && (clickType.equals("right"))) {
				if (!holding.canAimRight()) {
					holding.heldDownTicks += 1;
					holding.lastFired = 0;
					if (this.currentlyFiring == null)
						fireGun(holding);
				} else {
					checkAim();
				}
			} else if (((holding.canClickLeft) || (holding.canAimLeft())) && (clickType.equals("left"))) {
				if (!holding.canAimLeft()) {
					holding.heldDownTicks = 0;
					if (this.currentlyFiring == null)
						fireGun(holding);
				} else {
					checkAim();
				}
			}
		}
		return true;
	}

	protected void checkAim() {
		if (isAimedIn()) {
			this.controller.removePotionEffect(PotionEffectType.SLOW);
		} else {
			this.controller.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 12000, 4));
		}
	}

	private void fireGun(Gun gun) {
		if ((PermissionInterface.checkPermission(this.controller, gun.node)) || (!gun.needsPermission)) {
			if (gun.timer <= 0) {
				this.currentlyFiring = gun;
				gun.firing = true;
			}
		} else if ((gun.permissionMessage != null) && (gun.permissionMessage.length() > 0)) {
			this.controller.sendMessage(gun.permissionMessage);
		}
	}

	public void tick() {
		this.ticks += 1;
		if (this.controller != null) {
			ItemStack hand = this.controller.getItemInHand();
			this.lastHeldItem = hand;
			if ((this.ticks % 10 == 0) && (hand != null)) {
				Gun g = plugin.getGun(hand.getTypeId());
				if (g == null) {
					this.controller.removePotionEffect(PotionEffectType.SLOW);
				}
			}
			for (Gun g : guns) {
				if (g != null) {
					g.tick();
					
					if (this.controller.isDead()) {
						g.finishReloading();
					}

					if ((hand != null) && 
							(g.getGunType() == hand.getTypeId()) && 
							(isAimedIn()) && (!g.canAimLeft()) && (!g.canAimRight())) {
						this.controller.removePotionEffect(PotionEffectType.SLOW);
					}
					
					if ((this.currentlyFiring != null) && (g.timer <= 0) && (this.currentlyFiring.equals(g)))
						this.currentlyFiring = null;
				}
			}
		}
		renameGuns(this.controller);
  }

	protected void renameGuns(Player p) {
		Inventory inv = p.getInventory();
		ItemStack[] items = inv.getContents();
		for (int i = 0; i < items.length; i++)
			if (items[i] != null) {
				String name = getGunName(items[i]);
				if ((name != null) && (name.length() > 0))
					setName(items[i], name);
			}
	}

	protected List<Gun> getGunsByType(ItemStack item) {
		List<Gun> ret = new ArrayList<Gun>();
		for (Gun gun : guns) {
			if (gun.getGunMaterial() == item.getType()) {
				ret.add(gun);
			}
		}

		return ret;
  }

	protected String getGunName(ItemStack item) {
		String ret = "";
		List<Gun> tempgun = getGunsByType(item);
		int amtGun = tempgun.size();
		if (amtGun > 0) {
			for (int i = 0; i < tempgun.size(); i++) {
				if ((PermissionInterface.checkPermission(this.controller, ((Gun)tempgun.get(i)).node)) || (!((Gun)tempgun.get(i)).needsPermission)) {
					Gun current = (Gun)tempgun.get(i);
					if ((current.getGunMaterial() != null) && (current.getGunMaterial().getId() == item.getTypeId())) {
						byte gunDat = ((Gun)tempgun.get(i)).getGunTypeByte();
						byte itmDat = item.getData().getData();
						
						if ((gunDat == itmDat) || (((Gun)tempgun.get(i)).ignoreItemData)) {
							return getGunName(current);
						}
					}
				}
			}
		}
		return ret;
	}

	private String getGunName(Gun current) {
		String add = "";
		String refresh = "";
		if (current.hasClip) {
			int leftInClip = 0;
			int ammoLeft = 0;
			int maxInClip = current.maxClipSize;

			int currentAmmo = (int)Math.floor(InventoryHelper.amtItem(this.controller.getInventory(), current.getAmmoType(), current.getAmmoTypeByte()) / current.getAmmoAmtNeeded());
			ammoLeft = currentAmmo - maxInClip + current.roundsFired;
			if (ammoLeft < 0)
				ammoLeft = 0;
			leftInClip = currentAmmo - ammoLeft;
			add = ChatColor.YELLOW + "    «" + Integer.toString(leftInClip) + " │ " + Integer.toString(ammoLeft) + "»";
			if (current.reloading) {
				int reloadSize = 4;
				double reloadFrac = (current.getReloadTime() - current.gunReloadTimer) / current.getReloadTime();
				int amt = (int)Math.round(reloadFrac * reloadSize);
				for (int ii = 0; ii < amt; ii++) {
					refresh = refresh + "▪";
				}
				for (int ii = 0; ii < reloadSize - amt; ii++) {
					refresh = refresh + "▫";
				}

				add = ChatColor.RED + "    " + new StringBuffer(refresh).reverse() + "RELOADING" + refresh;
			}
		}
		String name = current.getName();
		return name + add;
	}
  
	protected ItemStack setName(ItemStack item, String name) {
		ItemMeta im = item.getItemMeta();
		im.setDisplayName(name);
		item.setItemMeta(im);

		return item;
	}

	public Player getPlayer() {
		return this.controller;
	}

	public void unload() {
		this.controller = null;
		this.currentlyFiring = null;
		
		for (Gun gun : guns) {
			gun.clear();
		}
	}

	public void reloadAllGuns() {
		for (Gun gun : guns) {
			if (gun != null) {
				gun.reloadGun();
				gun.finishReloading();
			}
		}
	}
  
	public boolean checkAmmo(Gun gun, int amount) {
		return InventoryHelper.amtItem(this.controller.getInventory(), gun.getAmmoType(), gun.getAmmoTypeByte()) >= amount;
	}

	public void removeAmmo(Gun gun, int amount) {
		if (amount == 0) {
			return;
		}
  		
		InventoryHelper.removeItem(this.controller.getInventory(), gun.getAmmoType(), gun.getAmmoTypeByte(), amount);
	}
  
	public ItemStack getLastItemHeld() {
		return this.lastHeldItem;
	}
  	
	public Gun getGun(int materialId) {
		for (Gun check : guns) {
			if (check.getGunType() == materialId)
				return check;
		}
	  
		return null;
	}
}