package org.mcmonkey.sentinel;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.persistence.Persist;
import net.citizensnpcs.api.trait.Trait;
import net.citizensnpcs.api.trait.trait.Owner;
import net.citizensnpcs.util.PlayerAnimation;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.craftbukkit.v1_9_R1.CraftWorld;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.regex.Pattern;

public class SentinelTrait extends Trait {

    public SentinelTrait() {
        super("sentinel");
    }

    @Persist("stats_ticksSpawned")
    public long stats_ticksSpawned = 0;

    @Persist("stats_timesSpawned")
    public long stats_timesSpawned = 0;

    @Persist("stats_arrowsFired")
    public long stats_arrowsFired = 0;

    @Persist("stats_potionsThrow")
    public long stats_potionsThrown = 0;

    @Persist("stats_fireballsFired")
    public long stats_fireballsFired = 0;

    @Persist("stats_punches")
    public long stats_punches = 0;

    @Persist("stats_attackAttempts")
    public long stats_attackAttempts = 0;

    @Persist("stats_damageTaken")
    public double stats_damageTaken = 0;

    @Persist("stats_damageGiven")
    public double stats_damageGiven = 0;

    @Persist("targets")
    public HashSet<SentinelTarget> targets = new HashSet<SentinelTarget>();

    @Persist("ignores")
    public HashSet<SentinelTarget> ignores = new HashSet<SentinelTarget>();

    @Persist("playerNameTargets")
    public List<String> playerNameTargets = new ArrayList<String>();

    @Persist("playerNameIgnores")
    public List<String> playerNameIgnores = new ArrayList<String>();

    @Persist("npcNameTargets")
    public List<String> npcNameTargets = new ArrayList<String>();

    @Persist("npcNameIgnores")
    public List<String> npcNameIgnores = new ArrayList<String>();

    @Persist("entityNameTargets")
    public List<String> entityNameTargets = new ArrayList<String>();

    @Persist("entityNameIgnores")
    public List<String> entityNameIgnores = new ArrayList<String>();

    @Persist("heldItemTargets")
    public List<String> heldItemTargets = new ArrayList<String>();

    @Persist("heldItemIgnores")
    public List<String> heldItemIgnores = new ArrayList<String>();

    @Persist("range")
    public double range = 20;

    @Persist("damage")
    public double damage = -1;

    @Persist("armor")
    public double armor = -1;

    @Persist("health")
    public double health = 20;

    @Persist("ranged_chase")
    public boolean rangedChase = false;

    @Persist("close_chase")
    public boolean closeChase = true;

    @Persist("invincible")
    public boolean invincible = false;

    @Persist("fightback")
    public boolean fightback = true;

    @Persist("attackRate")
    public int attackRate = 30;

    @Persist("healRate")
    public int healRate = 30;

    @EventHandler
    public void whenAttacksAreHappening(EntityDamageByEntityEvent event) {
        if (!npc.isSpawned()) {
            return;
        }
        if (event.getEntity().getUniqueId().equals(getLivingEntity().getUniqueId())) {
            event.setDamage(EntityDamageEvent.DamageModifier.ARMOR, -getArmor() * event.getDamage(EntityDamageEvent.DamageModifier.BASE));
            return;
        }
        if (event.getDamager().getUniqueId().equals(getLivingEntity().getUniqueId())) {
            event.setDamage(EntityDamageEvent.DamageModifier.BASE, getDamage());
        }
        if (event.getDamager() instanceof Projectile) {
            ProjectileSource source = ((Projectile) event.getDamager()).getShooter();
            if (source instanceof LivingEntity && ((LivingEntity) source).getUniqueId().equals(getLivingEntity().getUniqueId())) {
                event.setDamage(EntityDamageEvent.DamageModifier.BASE, getDamage());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void whenAttacksHappened(EntityDamageByEntityEvent event) {
        if (!npc.isSpawned()) {
            return;
        }
        if (event.getEntity().getUniqueId().equals(getLivingEntity().getUniqueId())) {
            stats_damageTaken += event.getFinalDamage();
            if (event.getDamager() instanceof LivingEntity) {
                currentTargets.add(event.getDamager().getUniqueId());
            }
            else if (event.getDamager() instanceof Projectile) {
                ProjectileSource source = ((Projectile) event.getDamager()).getShooter();
                if (source instanceof LivingEntity) {
                    currentTargets.add(((LivingEntity) source).getUniqueId());
                }
            }
            return;
        }
        if (event.getDamager().getUniqueId().equals(getLivingEntity().getUniqueId())) {
            stats_damageGiven += event.getFinalDamage();
            return;
        }
        Entity e = event.getDamager();
        if (!(e instanceof LivingEntity)) {
            if (e instanceof Projectile) {
                ProjectileSource source = ((Projectile)e).getShooter();
                if (source instanceof LivingEntity) {
                    e = (LivingEntity) source;
                    if (e.getUniqueId().equals(getLivingEntity().getUniqueId())) {
                        stats_damageGiven += event.getFinalDamage();
                    }
                }
            }
        }
    }

    @EventHandler
    public void whenAnEnemyDies(EntityDeathEvent event) {
        currentTargets.remove(event.getEntity().getUniqueId());
    }

    @Override
    public void onAttach() {
        FileConfiguration config = SentinelPlugin.instance.getConfig();
        attackRate = config.getInt("sentinel defaults.attack rate", 30);
        healRate
                = config.getInt("sentinel defaults.heal rate", 30);
        rangedChase = config.getBoolean("sentinel defaults.ranged chase target", false);
        closeChase = config.getBoolean("sentinel defaults.close chase target", true);
        armor = config.getDouble("sentinel defaults.armor", -1);
        damage = config.getDouble("sentinel defaults.damage", -1);
        health = config.getDouble("sentinel defaults.health", 20);
        if (npc.isSpawned()) {
            getLivingEntity().setMaxHealth(health);
            getLivingEntity().setHealth(health);
        }
        setInvincible(config.getBoolean("sentinel defaults.invincible", false));
        fightback = config.getBoolean("sentinel defaults.fightback", true);
        ignores.add(SentinelTarget.OWNER);
    }

    public void useItem() {
        if (npc.isSpawned() && getLivingEntity() instanceof Player) {
            PlayerAnimation.START_USE_MAINHAND_ITEM.play((Player) getLivingEntity());
            BukkitRunnable runner = new BukkitRunnable() {
                @Override
                public void run() {
                    if (npc.isSpawned() && getLivingEntity() instanceof Player) {
                        PlayerAnimation.STOP_USE_ITEM.play((Player) getLivingEntity());
                    }
                }
            };
            runner.runTaskLater(SentinelPlugin.instance, 10);
        }
    }

    public void swingWeapon() {
        if (npc.isSpawned() && getLivingEntity() instanceof Player) {
            PlayerAnimation.ARM_SWING.play((Player) getLivingEntity());
        }
    }

    public HashMap.SimpleEntry<Location, Vector> getLaunchDetail(Location target, Vector lead) {
        double speed;
        npc.faceLocation(target);
        double angt = -1;
        Location start = getLivingEntity().getEyeLocation().clone().add(getLivingEntity().getEyeLocation().getDirection());
        for (speed = 20; speed <= 45; speed += 5) {
            angt = SentinelUtilities.getArrowAngle(start, target, speed, 20);
            if (!Double.isInfinite(angt)) {
                break;
            }
        }
        if (Double.isInfinite(angt)) {
            return null;
        }
        double hangT = SentinelUtilities.hangtime(angt, speed, target.getY() - start.getY(), 20);
        Location to = target.clone().add(lead.clone().multiply(hangT));
        Vector relative = to.clone().subtract(start.toVector()).toVector();
        double deltaXZ = Math.sqrt(relative.getX() * relative.getX() + relative.getZ() * relative.getZ());
        if (deltaXZ == 0) {
            deltaXZ = 0.1;
        }
        for (speed = 20; speed < 45; speed += 5) {
            angt = SentinelUtilities.getArrowAngle(start, to, speed, 20);
            if (!Double.isInfinite(angt)) {
                break;
            }
        }
        if (Double.isInfinite(angt)) {
            return null;
        }
        relative.setY(Math.tan(angt) * deltaXZ);
        relative = relative.normalize();
        Vector normrel = relative.clone();
        speed = speed + (1.188 * hangT * hangT);
        relative = relative.multiply(speed / 20.0);
        start.setDirection(normrel);
        return new HashMap.SimpleEntry<Location, Vector>(start, relative);
    }

    public void firePotion(ItemStack potion, Location target, Vector lead) {
        stats_potionsThrown++;
        HashMap.SimpleEntry<Location, Vector> start = getLaunchDetail(target, lead);
        Entity entpotion = start.getKey().getWorld().spawnEntity(start.getKey(),
                potion.getType() == Material.SPLASH_POTION ? EntityType.SPLASH_POTION: EntityType.LINGERING_POTION);
        ((ThrownPotion) entpotion).setShooter(getLivingEntity());
        ((ThrownPotion) entpotion).setItem(potion);
        entpotion.setVelocity(start.getValue());
        swingWeapon();
    }

    public void fireArrow(ItemStack type, Location target, Vector lead) {
        stats_arrowsFired++;
        HashMap.SimpleEntry<Location, Vector> start = getLaunchDetail(target, lead);
        Entity arrow = start.getKey().getWorld().spawnEntity(start.getKey(),
                type.getType() == Material.SPECTRAL_ARROW ? EntityType.SPECTRAL_ARROW :
                        (type.getType() == Material.TIPPED_ARROW ? EntityType.TIPPED_ARROW : EntityType.ARROW));
        ((Projectile)arrow).setShooter(getLivingEntity());
        if (arrow instanceof TippedArrow) {
            ((TippedArrow)arrow).setBasePotionData(((PotionMeta)type.getItemMeta()).getBasePotionData());
            for (PotionEffect effect: ((PotionMeta)type.getItemMeta()).getCustomEffects()) {
                ((TippedArrow)arrow).addCustomEffect(effect, true);
            }
        }
        arrow.setVelocity(start.getValue());
        // TODO: Apply damage amount!
        // TODO: Prevent pick up if needed!
        useItem();
    }

    public void fireFireball(Location target) {
        swingWeapon();
        stats_fireballsFired++;
        npc.faceLocation(target);
        Vector forward = getLivingEntity().getEyeLocation().getDirection();
        Location spawnAt = getLivingEntity().getEyeLocation().clone().add(forward.clone().multiply(2));
        Entity ent = spawnAt.getWorld().spawnEntity(spawnAt, EntityType.SMALL_FIREBALL);
        ent.setVelocity(target.clone().subtract(spawnAt).toVector().normalize().multiply(4)); // TODO: Fiddle with '4'.
        // TODO: Apply damage amount!
    }

    public double getDamage() {
        if (damage < 0) {
            ItemStack weapon = getLivingEntity().getEquipment().getItemInMainHand();
            double baseDamage = 1;
            // TODO: Calculate damage!
            return baseDamage;
        }
        return damage;
    }

    public double getArmor() {
        if (armor < 0) {
            // TODO: Enchantments!
            double baseArmor = 0;
            ItemStack helmet = getLivingEntity().getEquipment().getHelmet();
            if (helmet != null && helmet.getType() == Material.DIAMOND_HELMET) {
                baseArmor += 0.12;
            }
            if (helmet != null && helmet.getType() == Material.GOLD_HELMET) {
                baseArmor += 0.08;
            }
            if (helmet != null && helmet.getType() == Material.IRON_HELMET) {
                baseArmor += 0.08;
            }
            if (helmet != null && helmet.getType() == Material.LEATHER_HELMET) {
                baseArmor += 0.04;
            }
            if (helmet != null && helmet.getType() == Material.CHAINMAIL_HELMET) {
                baseArmor += 0.08;
            }
            ItemStack chestplate = getLivingEntity().getEquipment().getChestplate();
            if (chestplate != null && chestplate.getType() == Material.DIAMOND_CHESTPLATE) {
                baseArmor += 0.32;
            }
            if (chestplate != null && chestplate.getType() == Material.GOLD_CHESTPLATE) {
                baseArmor += 0.20;
            }
            if (chestplate != null && chestplate.getType() == Material.IRON_CHESTPLATE) {
                baseArmor += 0.24;
            }
            if (chestplate != null && chestplate.getType() == Material.LEATHER_CHESTPLATE) {
                baseArmor += 0.12;
            }
            if (chestplate != null && chestplate.getType() == Material.CHAINMAIL_CHESTPLATE) {
                baseArmor += 0.20;
            }
            ItemStack leggings = getLivingEntity().getEquipment().getLeggings();
            if (leggings != null && leggings.getType() == Material.DIAMOND_LEGGINGS) {
                baseArmor += 0.24;
            }
            if (leggings != null && leggings.getType() == Material.GOLD_LEGGINGS) {
                baseArmor += 0.12;
            }
            if (leggings != null && leggings.getType() == Material.IRON_LEGGINGS) {
                baseArmor += 0.20;
            }
            if (leggings != null && leggings.getType() == Material.LEATHER_LEGGINGS) {
                baseArmor += 0.08;
            }
            if (leggings != null && leggings.getType() == Material.CHAINMAIL_LEGGINGS) {
                baseArmor += 0.16;
            }
            ItemStack boots = getLivingEntity().getEquipment().getBoots();
            if (boots != null && boots.getType() == Material.DIAMOND_BOOTS) {
                baseArmor += 0.12;
            }
            if (boots != null && boots.getType() == Material.GOLD_BOOTS) {
                baseArmor += 0.04;
            }
            if (boots != null && boots.getType() == Material.IRON_BOOTS) {
                baseArmor += 0.08;
            }
            if (boots != null && boots.getType() == Material.LEATHER_BOOTS) {
                baseArmor += 0.04;
            }
            if (boots != null && boots.getType() == Material.CHAINMAIL_BOOTS) {
                baseArmor += 0.04;
            }
            return Math.min(baseArmor, 0.80);
        }
        return armor;
    }

    public void punch(LivingEntity entity) {
        npc.faceLocation(entity.getLocation());
        swingWeapon();
        stats_punches++;
        entity.damage(getDamage(), getLivingEntity());
    }

    public void chase(LivingEntity entity) {
        npc.getNavigator().setTarget(entity.getLocation());
    }

    public ItemStack getArrow() {
        if (getLivingEntity() instanceof InventoryHolder) {
            Inventory inv = ((InventoryHolder)getLivingEntity()).getInventory();
            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack item = inv.getItem(i);
                if (item != null) {
                    Material mat = item.getType();
                    if (mat == Material.ARROW || mat == Material.TIPPED_ARROW || mat == Material.SPECTRAL_ARROW) {
                        return item;
                    }
                }
            }
        }
        return new ItemStack(Material.ARROW);
    }

    public void tryAttack(LivingEntity entity) {
        stats_attackAttempts++;
        if (usesBow()) {
            if (canSee(entity)) {
                if (timeSinceAttack < attackRate) {
                    return;
                }
                timeSinceAttack = 0;
                // TODO: Consume ammo if needed!
                fireArrow(getArrow(), entity.getEyeLocation(), entity.getVelocity());
            }
            else if (rangedChase) {
                chase(entity);
            }
        }
        else if (usesPotion()) {
            double distsq = entity.getLocation().distanceSquared(getLivingEntity().getLocation());
            if (canSee(entity)) {
                if (timeSinceAttack < attackRate) {
                    return;
                }
                timeSinceAttack = 0;
                // TODO: Consume ammo if needed!
                firePotion(getLivingEntity().getEquipment().getItemInMainHand(),
                        entity.getEyeLocation(), entity.getVelocity());
            }
            else if (rangedChase) {
                chase(entity);
            }
        }
        else if (usesFireball()) {
            if (canSee(entity)) {
                if (timeSinceAttack < attackRate) {
                    return;
                }
                timeSinceAttack = 0;
                // TODO: Consume ammo if needed!
                fireFireball(entity.getEyeLocation());
            }
            else if (rangedChase) {
                chase(entity);
            }
        }
        else if (usesLightning()) {
            if (canSee(entity)) {
                if (timeSinceAttack < attackRate) {
                    return;
                }
                timeSinceAttack = 0;
                // TODO: Consume ammo if needed!
                swingWeapon();
                entity.getWorld().strikeLightning(entity.getLocation()); // TODO: Audio?
            }
            else if (rangedChase) {
                chase(entity);
            }
        }
        else if (usesSpectral()) {
            if (canSee(entity)) {
                if (timeSinceAttack < attackRate) {
                    return;
                }
                timeSinceAttack = 0;
                // TODO: Consume ammo if needed!
                swingWeapon();
                // TODO: Audio?
                entity.setGlowing(true);
            }
            else if (rangedChase) {
                chase(entity);
            }
        }
        else {
            double distsq = entity.getLocation().distanceSquared(getLivingEntity().getLocation());
            if (distsq < 3 * 3) {
                if (timeSinceAttack < attackRate) {
                    return;
                }
                timeSinceAttack = 0;
                // TODO: Damage sword if needed!
                punch(entity);
            }
            else {
                chase(entity);
            }
        }
    }

    public boolean canSee(LivingEntity entity) {
        return getLivingEntity().hasLineOfSight(entity);
    }

    public LivingEntity getLivingEntity() {
        // Not a good idea to turn a non-living NPC into a Sentinel for now.
        return (LivingEntity) npc.getEntity();
    }

    public boolean isRanged() {
        return usesBow() || usesFireball();
    }

    public boolean usesBow() {
        return getLivingEntity().getEquipment().getItemInMainHand().getType() == Material.BOW;
    }

    public boolean usesFireball() {
        return getLivingEntity().getEquipment().getItemInMainHand().getType() == Material.BLAZE_ROD;
    }

    public boolean usesLightning() {
        return getLivingEntity().getEquipment().getItemInMainHand().getType() == Material.NETHER_STAR;
    }

    public boolean usesSpectral() {
        return getLivingEntity().getEquipment().getItemInMainHand().getType() == Material.SPECTRAL_ARROW;
    }

    public boolean usesPotion() {
        Material type = getLivingEntity().getEquipment().getItemInMainHand().getType();
        return type == Material.SPLASH_POTION || type == Material.LINGERING_POTION;
    }

    public boolean shouldTarget(LivingEntity entity) {
        if (entity.getUniqueId().equals(getLivingEntity().getUniqueId())) {
            return false;
        }
        return isTargeted(entity) && !isIgnored(entity);
    }

    private HashSet<UUID> currentTargets = new HashSet<UUID>(); // TODO: Apology / cancel attack system!

    public boolean isRegexTargeted(String name, List<String> regexes) {
        for (String str: regexes) {
            Pattern pattern = Pattern.compile(str, Pattern.CASE_INSENSITIVE);
            if (pattern.matcher(name).matches()) {
                return true;
            }
        }
        return false;
    }

    public boolean isIgnored(LivingEntity entity) {
        if (entity.hasMetadata("NPC")) {
            return ignores.contains(SentinelTarget.NPCS) ||
                    isRegexTargeted(CitizensAPI.getNPCRegistry().getNPC(entity).getName(), npcNameIgnores);
        }
        else if (entity instanceof Player) {
            if (isRegexTargeted(((Player) entity).getName(), playerNameIgnores)) {
                return true;
            }
        }
        else if (isRegexTargeted(entity.getCustomName() == null ? entity.getType().name(): entity.getCustomName(), entityNameIgnores)) {
            return true;
        }
        if (ignores.contains(SentinelTarget.OWNER) && entity.getUniqueId().equals(npc.getTrait(Owner.class).getOwnerId())) {
            return true;
        }
        HashSet<SentinelTarget> possible = SentinelPlugin.entityToTargets.get(entity.getType());
        for (SentinelTarget poss: possible) {
            if (ignores.contains(poss)) {
                return true;
            }
        }
        if (entity.getEquipment() != null && entity.getEquipment().getItemInMainHand() != null
                && isRegexTargeted(entity.getEquipment().getItemInMainHand().getType().name(), heldItemIgnores)) {
            return true;
        }
        return false;
    }

    public boolean isTargeted(LivingEntity entity) {
        if (currentTargets.contains(entity.getUniqueId())) {
            return true;
        }
        if (entity.hasMetadata("NPC")) {
            return targets.contains(SentinelTarget.NPCS) ||
                    isRegexTargeted(CitizensAPI.getNPCRegistry().getNPC(entity).getName(), npcNameTargets);
        }
        if (entity instanceof Player) {
            if (isRegexTargeted(((Player) entity).getName(), playerNameTargets)) {
                return true;
            }
        }
        else if (isRegexTargeted(entity.getCustomName() == null ? entity.getType().name(): entity.getCustomName(), entityNameTargets)) {
            return true;
        }
        if (targets.contains(SentinelTarget.OWNER) && entity.getUniqueId().equals(npc.getTrait(Owner.class).getOwnerId())) {
            return true;
        }
        HashSet<SentinelTarget> possible = SentinelPlugin.entityToTargets.get(entity.getType());
        for (SentinelTarget poss: possible) {
            if (targets.contains(poss)) {
                return true;
            }
        }
        if (entity.getEquipment() != null && entity.getEquipment().getItemInMainHand() != null
                && isRegexTargeted(entity.getEquipment().getItemInMainHand().getType().name(), heldItemTargets)) {
            return true;
        }
        return false;
    }

    public int cTick = 0;

    /**
     * This method searches for the nearest targetable entity with direct line-of-sight.
     * Failing a direct line of sight, the nearest entity in range at all will be chosen.
     */
    public LivingEntity findBestTarget() {
        double rangesquared = range * range;
        Location pos = getLivingEntity().getEyeLocation();
        LivingEntity closest = null;
        for (LivingEntity ent: getLivingEntity().getWorld().getLivingEntities()) {
            double dist = ent.getEyeLocation().distanceSquared(pos);
            if (dist < rangesquared && shouldTarget(ent) && canSee(ent)) {
                rangesquared = dist;
                closest = ent;
            }
        }
        if (closest == null) {
            // NOTE: Possibly can be optimized by retrieving a list from the above logic?
            for (LivingEntity ent: getLivingEntity().getWorld().getLivingEntities()) {
                double dist = ent.getEyeLocation().distanceSquared(pos);
                if (dist < rangesquared && shouldTarget(ent)) {
                    rangesquared = dist;
                    closest = ent;
                }
            }
        }
        return closest;
    }

    public long timeSinceAttack = 0;

    public long timeSinceHeal = 0;

    private Entity getEntityForID(UUID id) {
        // TODO: Remove NMS here!
        net.minecraft.server.v1_9_R1.Entity nmsEntity = ((CraftWorld) getLivingEntity().getWorld()).getHandle().getEntity(id);
        if (nmsEntity != null) {
            return nmsEntity.getBukkitEntity();
        }
        return null;
    }

    public void runUpdate() {
        timeSinceAttack += SentinelPlugin.instance.tickRate;
        timeSinceHeal += SentinelPlugin.instance.tickRate;
        if (healRate > 0 && timeSinceHeal > healRate && getLivingEntity().getHealth() < health) {
            getLivingEntity().setHealth(Math.min(getLivingEntity().getHealth() + 1.0, health));
            timeSinceHeal = 0;
        }
        for (UUID uuid : new HashSet<UUID>(currentTargets)) {
            Entity e = getEntityForID(uuid);
            if (e == null) {
                currentTargets.remove(uuid);
            }
            else if (e.getLocation().distanceSquared(getLivingEntity().getLocation()) > range * range * 4) {
                currentTargets.remove(uuid);
            }
        }
        LivingEntity target = findBestTarget();
        if (target == null) {
            return;
        }
        tryAttack(target);
    }

    @Override
    public void run() {
        if (!npc.isSpawned()) {
            return;
        }
        stats_ticksSpawned++;
        cTick++;
        if (cTick >= SentinelPlugin.instance.tickRate) {
            cTick = 0;
            runUpdate();
        }
    }

    @Override
    public void onSpawn() {
        stats_timesSpawned++;
        setHealth(health);
        setInvincible(invincible);
    }

    @Override
    public void onDespawn() {
        currentTargets.clear();
    }

    public void setHealth(double heal) {
        health = heal;
        if (npc.isSpawned()) {
            getLivingEntity().setMaxHealth(health);
        }
    }


    public void setInvincible(boolean inv) {
        invincible = inv;
        npc.setProtected(invincible);
    }
}