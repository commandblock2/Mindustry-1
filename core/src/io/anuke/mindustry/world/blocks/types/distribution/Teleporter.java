package io.anuke.mindustry.world.blocks.types.distribution;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectSet;
import io.anuke.mindustry.content.Liquids;
import io.anuke.mindustry.content.fx.BlockFx;
import io.anuke.mindustry.entities.TileEntity;
import io.anuke.mindustry.resource.Item;
import io.anuke.mindustry.resource.Liquid;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.types.PowerBlock;
import io.anuke.ucore.core.Effects;
import io.anuke.ucore.core.Effects.Effect;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.graphics.Fill;
import io.anuke.ucore.graphics.Hue;
import io.anuke.ucore.graphics.Lines;
import io.anuke.ucore.scene.ui.ButtonGroup;
import io.anuke.ucore.scene.ui.ImageButton;
import io.anuke.ucore.scene.ui.layout.Table;
import io.anuke.ucore.util.Mathf;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class Teleporter extends PowerBlock{
	public static final Color[] colorArray = {Color.ROYAL, Color.ORANGE, Color.SCARLET, Color.LIME,
			Color.PURPLE, Color.GOLD, Color.PINK, Color.LIGHT_GRAY};
	public static final int colors = colorArray.length;

	protected int timerTeleport = timers++;

	private static ObjectSet<Tile>[] teleporters = new ObjectSet[colors];
	private static Color color = new Color();
	private static byte lastColor = 0;

	private Array<Tile> removal = new Array<>();
	private Array<Tile> returns = new Array<>();

	protected float warmupTime = 60f;
	protected float teleportMax = 400f;
	protected float liquidUse = 0.3f;
	protected float powerUse = 0.3f;
	protected Liquid inputLiquid = Liquids.cryofluid;
	protected Effect activateEffect = BlockFx.teleportActivate;
	protected Effect teleportEffect = BlockFx.teleport;
	protected Effect teleportOutEffect = BlockFx.teleportOut;

	static{
		for(int i = 0; i < colors; i ++){
			teleporters[i] = new ObjectSet<>();
		}
	}
	
	public Teleporter(String name) {
		super(name);
		update = true;
		solid = true;
		health = 80;
		powerCapacity = 300f;
		size = 3;
		itemCapacity = 100;
		hasLiquids = true;
		liquidCapacity = 100f;
	}

	@Override
	public void configure(Tile tile, byte data) {
		TeleporterEntity entity = tile.entity();
		if(entity != null){
			entity.color = data;
			entity.items.clear();
		}
	}

	@Override
	public void setStats(){
		super.setStats();
	}

	@Override
	public void placed(Tile tile){
		tile.<TeleporterEntity>entity().color = lastColor;
		setConfigure(tile, lastColor);
	}
	
	@Override
	public void draw(Tile tile){
		super.draw(tile);

		TeleporterEntity entity = tile.entity();
		float time = entity.time;
		float rad = entity.activeScl;

		Draw.color(getColor(tile, 0));
		Draw.rect("teleporter-top", tile.drawx(), tile.drawy());
		Draw.reset();

		if(rad <= 0.0001f) return;

		Draw.color(getColor(tile, 0));

		Fill.circle(tile.drawx(), tile.drawy(), rad*(7f + Mathf.absin(time+55, 8f, 1f)));

		Draw.color(getColor(tile, -1));

		Fill.circle(tile.drawx(), tile.drawy(), rad*(2f + Mathf.absin(time, 7f, 3f)));

		for(int i = 0; i < 11; i ++){
			Lines.swirl(tile.drawx(), tile.drawy(),
					rad*(2f + i/3f + Mathf.sin(time - i *75, 20f + i, 3f)),
					0.3f + Mathf.sin(time + i *33, 10f + i, 0.1f),
					time * (1f + Mathf.randomSeedRange(i + 1, 1f)) + Mathf.randomSeedRange(i, 360f));
		}

		Draw.color(getColor(tile, 1));

		Lines.stroke(2f);
		Lines.circle(tile.drawx(), tile.drawy(), rad*(7f + Mathf.absin(time+55, 8f, 1f)));
		Lines.stroke(1f);

		for(int i = 0; i < 11; i ++){
			Lines.swirl(tile.drawx(), tile.drawy(),
					rad*(3f + i/3f + Mathf.sin(time + i *93, 20f + i, 3f)),
					0.2f + Mathf.sin(time + i *33, 10f + i, 0.1f),
					time * (1f + Mathf.randomSeedRange(i + 1, 1f)) + Mathf.randomSeedRange(i, 360f));
		}

		Draw.reset();
	}
	
	@Override
	public void update(Tile tile){
		TeleporterEntity entity = tile.entity();

		teleporters[entity.color].add(tile);

		if(entity.items.totalItems() > 0){
			tryDump(tile);
		}

		if(!entity.active){
			entity.activeScl = Mathf.lerpDelta(entity.activeScl, 0f, 0.01f);

			if(entity.power.amount >= powerCapacity){
				entity.active = true;
				entity.power.amount = 0f;
				Effects.effect(activateEffect, getColor(tile, 0), tile.drawx(), tile.drawy());
			}
		}else {
			entity.activeScl = Mathf.lerpDelta(entity.activeScl, 1f, 0.015f);

			float powerUsed = Math.min(powerCapacity, powerUse * Timers.delta());

			if (entity.power.amount >= powerUsed) {
				entity.power.amount -= powerUsed;
				entity.powerLackScl = Mathf.lerpDelta(entity.powerLackScl, 0f, 0.01f);
			}else{
				entity.powerLackScl = Mathf.lerpDelta(entity.powerLackScl, 1f, 0.01f);
			}

			if(entity.powerLackScl >= 0.999f){
				catastrophicFailure(tile);
			}

			//TODO draw warning info!

			if (entity.teleporting) {
				entity.speedScl = Mathf.lerpDelta(entity.speedScl, 2f, 0.01f);
				float liquidUsed = Math.min(liquidCapacity, liquidUse * Timers.delta());

				if (entity.liquids.amount >= liquidUsed) {
					entity.liquids.amount -= liquidUsed;
				} else {
					catastrophicFailure(tile);
				}
			} else {
				entity.speedScl = Mathf.lerpDelta(entity.speedScl, 1f, 0.04f);
			}

			entity.time += Timers.delta() * entity.speedScl;

			if (entity.items.totalItems() == itemCapacity && entity.power.amount >= powerCapacity &&
					entity.timer.get(timerTeleport, teleportMax)) {
				Array<Tile> testLinks = findLinks(tile);

				if (testLinks.size == 0) return;

				entity.teleporting = true;

				Effects.effect(teleportEffect, getColor(tile, 0), tile.drawx(), tile.drawy());
				Timers.run(warmupTime, () -> {
					Array<Tile> links = findLinks(tile);

					for (Tile other : links) {
						int canAccept = itemCapacity - other.entity.items.totalItems();
						int total = entity.items.totalItems();
						if (total == 0) break;
						Effects.effect(teleportOutEffect, getColor(tile, 0), other.drawx(), other.drawy());
						for (int i = 0; i < canAccept && i < total; i++) {
							other.entity.items.addItem(entity.items.takeItem(), 1);
						}
					}
					Effects.effect(teleportOutEffect, getColor(tile, 0), tile.drawx(), tile.drawy());
					entity.power.amount = 0f;
					entity.teleporting = false;
				});
			}
		}
	}

	@Override
	public boolean isConfigurable(Tile tile){
		return true;
	}
	
	@Override
	public void buildTable(Tile tile, Table table){
		TeleporterEntity entity = tile.entity();

		ButtonGroup<ImageButton> group = new ButtonGroup<>();
		Table cont = new Table();
		cont.margin(4);
		cont.marginBottom(5);

		cont.add().colspan(4).height(145f);
		cont.row();

		for(int i = 0; i < colors; i ++){
			final int f = i;
			ImageButton button = cont.addImageButton("white", "toggle", 24, () -> {
				lastColor = (byte)f;
				setConfigure(tile, (byte)f);
			}).size(34, 38).padBottom(-5.1f).group(group).get();
			button.getStyle().imageUpColor = colorArray[f];
			button.setChecked(entity.color == f);

			if(i%4 == 3){
				cont.row();
			}
		}

		table.add(cont);
	}
	
	@Override
	public boolean acceptItem(Item item, Tile tile, Tile source){
		TeleporterEntity entity = tile.entity();
		return entity.items.totalItems() < itemCapacity;
	}
	
	@Override
	public TileEntity getEntity(){
		return new TeleporterEntity();
	}

	@Override
	public boolean acceptLiquid(Tile tile, Tile source, Liquid liquid, float amount) {
		return super.acceptLiquid(tile, source, liquid, amount) && liquid == inputLiquid;
	}

	private void catastrophicFailure(Tile tile){
		//TODO fail gloriously
	}

	private Color getColor(Tile tile, int shift){
		TeleporterEntity entity = tile.entity();

		Color target = colorArray[entity.color];
		float ss = 0.5f;
		float bs = 0.2f;

		return Hue.shift(Hue.multiply(color.set(target), 1, ss), 2, shift * bs + (entity.speedScl - 1f)/3f);
	}
	
	private Array<Tile> findLinks(Tile tile){
		TeleporterEntity entity = tile.entity();
		
		removal.clear();
		returns.clear();
		
		for(Tile other : teleporters[entity.color]){
			if(other != tile){
				if(other.block() instanceof Teleporter){
					TeleporterEntity oe = other.entity();
					if(!oe.active) continue;
					if(oe.color != entity.color){
						removal.add(other);
					}else if(other.entity.items.totalItems() == 0){
						returns.add(other);
					}
				}else{
					removal.add(other);
				}
			}
		}

		for(Tile remove : removal)
			teleporters[entity.color].remove(remove);
		
		return returns;
	}

	public static class TeleporterEntity extends TileEntity{
		public byte color = 0;
		public boolean teleporting;
		public boolean active;
		public float activeScl = 0f;
		public float speedScl = 1f;
		public float powerLackScl;
		public float time;
		
		@Override
		public void write(DataOutputStream stream) throws IOException{
			stream.writeByte(color);
		}
		
		@Override
		public void read(DataInputStream stream) throws IOException{
			color = stream.readByte();
		}
	}

}
