package de.clientapi.airswap;


import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.ComboAbility;
import com.projectkorra.projectkorra.ability.AirAbility;
import com.projectkorra.projectkorra.ability.util.ComboManager;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.util.ClickType;
import com.projectkorra.projectkorra.util.ParticleEffect;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayList;

public class AirSwap extends AirAbility implements ComboAbility, AddonAbility {
    private long cooldown;
    private Location origin;
    private Location targetLocation;
    private Vector direction;
    private boolean swapped;
    private LivingEntity target;
    private double swapRange;

    public AirSwap(Player player) {
        super(player);
        if (!this.bPlayer.canBendIgnoreBinds(this)) return;

        setFields();
        start();
        this.bPlayer.addCooldown(this);
    }

    private void setFields() {
        this.cooldown = ConfigManager.getConfig().getLong("ExtraAbilities.ClientAPI.Air.AirSwap.Cooldown", 7000);
        this.swapRange = ConfigManager.getConfig().getDouble("ExtraAbilities.ClientAPI.Air.AirSwap.Range", 15);
        this.origin = player.getLocation();
        this.swapped = false;
    }

    @Override
    public long getCooldown() {
        return cooldown;
    }

    @Override
    public Location getLocation() {
        return origin;
    }

    @Override
    public String getName() {
        return "AirSwap";
    }

    @Override
    public boolean isHarmlessAbility() {
        return true;
    }

    @Override
    public boolean isSneakAbility() {
        return true;
    }

    @Override
    public void progress() {
        if (!bPlayer.canBendIgnoreBindsCooldowns(this)) {
            remove();
            return;
        }

        if (!swapped) {
            target = getTargetEntity(player, swapRange);
            if (target != null) {
                targetLocation = target.getLocation();
                direction = targetLocation.toVector().subtract(player.getLocation().toVector()).normalize().multiply(0.5);
                ParticleEffect.CLOUD.display(player.getLocation(), 10, 0.5, 0.5, 0.5, 0.05);
                ParticleEffect.CLOUD.display(targetLocation, 10, 0.5, 0.5, 0.5, 0.05);

                new BukkitRunnable() {
                    public void run() {
                        pushUpward();
                    }
                }.runTaskLater(ProjectKorra.plugin, 5L);

                swapped = true;
            } else {
                new BukkitRunnable() {
                    public void run() {
                        player.setVelocity(new Vector(0, 1, 0));
                        ParticleEffect.CLOUD.display(player.getLocation(), 10, 0.5, 0.5, 0.5, 0.05);
                    }
                }.runTaskLater(ProjectKorra.plugin, 5L);
                remove();
            }
        } else {
            remove();
        }
    }

    private LivingEntity getTargetEntity(Player player, double range) {
        RayTraceResult result = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                range,
                entity -> entity instanceof LivingEntity && entity != player
        );

        return result != null && result.getHitEntity() instanceof LivingEntity ? (LivingEntity) result.getHitEntity() : null;
    }

    private void pushUpward() {
        player.setVelocity(new Vector(0, 0.5, 0));
        target.setVelocity(new Vector(0, 0.53, 0)); // feels better

        new BukkitRunnable() {
            @Override
            public void run() {
                swapPositions();
            }
        }.runTaskLater(ProjectKorra.plugin, 8L);
    }

    // funny ellipse
    private double elipfactor(double factor,  double x){
        return factor*(x*x-x)+1;
    }


    // thanks to @turpo2098 for fixxing my horrendous math
    private Vector rMatrix(double angle, Vector vector) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double x = vector.getX() * cos - vector.getZ() * sin;
        double z = vector.getX() * sin + vector.getZ() * cos;
        return new Vector(x, vector.getY(), z);
    }

    private void swapPositions() {
        if (target != null && player.isOnline()) {
            Location playerLocation = player.getLocation();
            Location targetLocation = target.getLocation();



            new BukkitRunnable() {
                private int ticks = 0;
                private final int duration = 20;
                private final Vector playerStart = playerLocation.toVector();
                private final Vector targetStart = targetLocation.toVector();
                private final Vector playerEnd = targetStart.clone();
                private final Vector targetEnd = playerStart.clone();

                @Override
                public void run() {
                    ticks++;
                    double progress = (double) ticks / duration;
                    if (progress >= 1.2) { // feels better
                        cancel();
                        remove();
                    } else {
                        double angle = progress * Math.PI;
                        double factor = elipfactor(2, progress);
                        Vector U = targetStart.clone().subtract(playerStart);
                        Vector C = playerStart.clone().add(U.clone().multiply(0.5));

                        Vector playerCurrent = rMatrix(angle, U.clone().multiply(-1)).multiply(factor).add(C);
                        Vector targetCurrent = rMatrix(angle, U).multiply(factor).add(C);
                        Vector playerVelocity = playerCurrent.subtract(player.getLocation().toVector()).multiply(0.08); // changes the speed of the swap
                        Vector targetVelocity = targetCurrent.subtract(target.getLocation().toVector()).multiply(0.08); // changes the speed of the swap

                        player.setVelocity(playerVelocity);
                        target.setVelocity(targetVelocity);
                        ParticleEffect.SWEEP_ATTACK.display(player.getLocation(), 1, 0.1, 0.1, 0.1, 0.01);
                        ParticleEffect.SWEEP_ATTACK.display(target.getLocation(), 1, 0.1, 0.1, 0.1, 0.01);
                    }
                }
            }.runTaskTimer(ProjectKorra.plugin, 0L, 1L);
        }
    }

    @Override
    public String getAuthor() {
        return "ClientAPI";
    }

    @Override
    public String getDescription() {
        return "AirSwap allows the bender to swap positions with a target";
    }

    @Override
    public String getInstructions() {
        return "AirShield (Shift) > AirBlast (Click) > AirSuction (Click)";
    }

    @Override
    public String getVersion() {
        return "0.2";
    }

    @Override
    public void load() {
        ConfigManager.getConfig().addDefault("ExtraAbilities.ClientAPI.Air.AirSwap.Cooldown", 7000);
        ConfigManager.getConfig().addDefault("ExtraAbilities.ClientAPI.Air.AirSwap.Range", 20);
        ConfigManager.defaultConfig.save();
    }

    @Override
    public void stop() {
        remove();
    }

    @Override
    public Object createNewComboInstance(Player player) {
        return new AirSwap(player);
    }

    @Override
    public ArrayList<ComboManager.AbilityInformation> getCombination() {
        ArrayList<ComboManager.AbilityInformation> combo = new ArrayList<>();
        combo.add(new ComboManager.AbilityInformation("AirShield", ClickType.SHIFT_DOWN));
        combo.add(new ComboManager.AbilityInformation("AirBlast", ClickType.LEFT_CLICK));
        combo.add(new ComboManager.AbilityInformation("AirSuction", ClickType.LEFT_CLICK));
        return combo;
    }
}