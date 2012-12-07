package fr.neatmonster.nocheatplus.checks.moving;

import net.minecraft.server.v1_4_5.DamageSource;
import net.minecraft.server.v1_4_5.EntityPlayer;

import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_4_5.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

import fr.neatmonster.nocheatplus.checks.Check;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.utilities.CheckUtils;
import fr.neatmonster.nocheatplus.utilities.PlayerLocation;

/*
 * M"""""""`YM          MM""""""""`M          dP dP 
 * M  mmmm.  M          MM  mmmmmmmM          88 88 
 * M  MMMMM  M .d8888b. M'      MMMM .d8888b. 88 88 
 * M  MMMMM  M 88'  `88 MM  MMMMMMMM 88'  `88 88 88 
 * M  MMMMM  M 88.  .88 MM  MMMMMMMM 88.  .88 88 88 
 * M  MMMMM  M `88888P' MM  MMMMMMMM `88888P8 dP dP 
 * MMMMMMMMMMM          MMMMMMMMMMMM                
 */
/**
 * A check to see if people cheat by tricking the server to not deal them fall damage.
 */
public class NoFall extends Check {

    /**
     * Instantiates a new no fall check.
     */
    public NoFall() {
        super(CheckType.MOVING_NOFALL);
    }
    
    /**
     * Calculate the damage in hearts from the given fall distance.
     * @param fallDistance
     * @return
     */
    protected static final int getDamage(final float fallDistance){
        return (int) Math.round(fallDistance - 3.0);
    }
    
    /**
     * Deal damage if appropriate. To be used for if the player is on ground somehow.
     * @param mcPlayer
     * @param data
     * @param y
     */
    private static final void handleOnGround(final EntityPlayer mcPlayer, final MovingData data, final double y, final MovingConfig cc, final boolean reallyOnGround) {
//        final int pD = getDamage(mcPlayer.fallDistance);
//        final int nfD = getDamage(data.noFallFallDistance);
//        final int yD = getDamage((float) (data.noFallMaxY - y));
//        final int maxD = Math.max(Math.max(pD, nfD), yD);
        final int maxD = getDamage(Math.max((float) (data.noFallMaxY - y), Math.max(data.noFallFallDistance, mcPlayer.fallDistance)));
        if (maxD > 0){
            // Damage to be dealt.
            // TODO: more effects like sounds, maybe use custom event with violation added.
            if (cc.debug) System.out.println(mcPlayer.name + " NoFall deal damage" + (reallyOnGround ? "" : "violation") + ": " + maxD);
            // TODO: might not be necessary: if (mcPlayer.invulnerableTicks <= 0)  [no damage event for resetting]
            data.noFallSkipAirCheck = true;
			dealFallDamage(mcPlayer, maxD);
        }
        else data.clearNoFallData();
    }

    private static void dealFallDamage(EntityPlayer mcPlayer, int damage) {
    	final EntityDamageEvent event = new EntityDamageEvent(mcPlayer.getBukkitEntity(), DamageCause.FALL, damage);
        Bukkit.getPluginManager().callEvent(event);
        if (!event.isCancelled()){
            mcPlayer.damageEntity(DamageSource.FALL, event.getDamage());
        }
        // TODO: let this be done by the damage event (!).
//        data.clearNoFallData(); // -> currently done in the damage eventhandling method.
        mcPlayer.fallDistance = 0;
	}

	/**
     * Checks a player.
     * 
     * @param player
     *            the player
     * @param from
     *            the from
     * @param to
     *            the to
     */
    public void check(final Player player, final PlayerLocation from, final PlayerLocation to, final MovingData data, final MovingConfig cc) {
    	
    	final double fromY = from.getY();
    	final double toY = to.getY();
    	
    	// TODO: account for player.getLocation.getY (how exactly ?)
		final double yDiff = toY - fromY;
		
		final double oldNFDist = data.noFallFallDistance;
		
		// Adapt yOnGround if necessary (sf uses another setting).
		if (yDiff < 0) {
			// In fact this is somewhat heuristic, but it seems to work well.
			// Missing on-ground seems to happen with running down pyramids rather.
			if (from.getyOnGround() != cc.noFallyOnGround && fromY - from.getBlockY() < cc.noFallyOnGround) from.setyOnGround(cc.noFallyOnGround);
			if (to.getyOnGround() != cc.noFallyOnGround && toY - to.getBlockY() < cc.noFallyOnGround) to.setyOnGround(cc.noFallyOnGround);
		}
        
        // TODO: Distinguish water depth vs. fall distance!
        
        final boolean fromOnGround = from.isOnGround();
        final boolean fromReset = from.isResetCond();
        final boolean toOnGround = to.isOnGround();
        final boolean toReset = to.isResetCond();
        
        final EntityPlayer mcPlayer = ((CraftPlayer) player).getHandle();
        
        
        // TODO: early returns (...) 
        
        final double pY =  player.getLocation().getY();
        final double minY = Math.min(fromY, Math.min(toY, pY));
        
        if (fromReset){
            // Just reset.
            data.clearNoFallData();
        }
        else if (fromOnGround || data.noFallAssumeGround){
            // Check if to deal damage (fall back damage check).
            if (cc.noFallDealDamage) handleOnGround(mcPlayer, data, minY, cc, true);
            else{
                mcPlayer.fallDistance = Math.max(mcPlayer.fallDistance, Math.max(data.noFallFallDistance, (float) (data.noFallMaxY - minY)));
                data.clearNoFallData();
            }
        }
        else if (toReset){
            // Just reset.
            data.clearNoFallData();
        }
        else if (toOnGround){
            // Check if to deal damage.
            if (yDiff < 0){
            	// In this case the player has traveled further: add the difference.
            	data.noFallFallDistance -= yDiff;
            }
            if (cc.noFallDealDamage) handleOnGround(mcPlayer, data, minY, cc, true);
            else{
                mcPlayer.fallDistance = Math.max(mcPlayer.fallDistance, Math.max(data.noFallFallDistance, (float) (data.noFallMaxY - minY)));
                data.clearNoFallData();
            }
        }
        else{
            // Ensure fall distance is correct, or "anyway"?
        }
        
        // Set reference y for nofall (always).
        // TODO: Consider setting this before handleOnGround (at least for resetTo).
        data.noFallMaxY = Math.max(Math.max(fromY, Math.max(toY, pY)), data.noFallMaxY);
        
        // TODO: fall distance might be behind (!)
        // TODO: should be the data.noFallMaxY be counted in ?
        data.noFallFallDistance = Math.max(mcPlayer.fallDistance, data.noFallFallDistance);
        
        // Add y distance.
        if (!toReset && !toOnGround && yDiff < 0){
            data.noFallFallDistance -= yDiff;
        }
        
        if (cc.debug) System.out.println(player.getName() + " NoFall: mc=" + CheckUtils.fdec3.format(mcPlayer.fallDistance) +" / nf=" + CheckUtils.fdec3.format(data.noFallFallDistance) + (oldNFDist < data.noFallFallDistance ? " (+" + CheckUtils.fdec3.format(data.noFallFallDistance - oldNFDist) + ")" : ""));
        
    }
    
    /**
     * Quit or kick: adjust fall distance if necessary.
     * @param player
     */
    public void onLeave(final Player player) {
        final MovingData data = MovingData.getData(player);
        final float fallDistance = player.getFallDistance();
        if (data.noFallFallDistance - fallDistance > 0){
            // Might use tolerance, might log, might use method (compare: MovingListener.onEntityDamage).
            // Might consider triggering violations here as well.
            final float yDiff = (float) (data.noFallMaxY - player.getLocation().getY());
            final float maxDist = Math.max(yDiff, Math.max(data.noFallFallDistance, fallDistance));
            player.setFallDistance(maxDist);
        }
    }

    /**
     * This is called if a player fails a check and gets set back, to avoid using that to avoid fall damage the player might be dealt damage here. 
     * @param player
     * @param data
     */
	public void checkDamage(final Player player, final MovingData data, final double y) {
		final MovingConfig cc = MovingConfig.getConfig(player);
		
		// Deal damage.
		handleOnGround(((CraftPlayer) player).getHandle(), data, y, cc, false);
	}
	
}
