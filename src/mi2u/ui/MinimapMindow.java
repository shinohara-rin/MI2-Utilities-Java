package mi2u.ui;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.input.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.*;
import arc.scene.event.*;
import arc.scene.ui.layout.*;
import arc.struct.Seq;
import arc.util.*;
import arc.util.pooling.*;
import mi2u.MI2UTmp;
import mi2u.MI2UVars;
import mi2u.input.InputOverwrite;
import mi2u.io.*;
import mindustry.core.*;
import mindustry.entities.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.input.*;
import mindustry.io.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.blocks.distribution.Sorter;

import static mindustry.Vars.*;

public class MinimapMindow extends Mindow2{
    public static Minimap2 m = new Minimap2(200f);
    public MinimapMindow(){
        super("MindowMap");
        mindowName = "MindowMap";
    }

    @Override
    public void setupCont(Table cont){
        cont.clear();
        m.setMapSize(MI2USettings.getInt(mindowName + ".size", 200));
        cont.add(m).fill();
        cont.row();
        cont.table(t -> {
            t.table(tt -> {
                tt.label(() -> "    " + Strings.fixed(World.conv(player.x), 1) + ", "+ Strings.fixed(World.conv(player.y), 1));
                tt.row();
                tt.label(() -> Iconc.commandAttack + "  " + Strings.fixed(World.conv(Core.input.mouseWorldX()), 1) + ", "+ Strings.fixed(World.conv(Core.input.mouseWorldY()), 1));
            }).growX();
            t.table(tt -> {
                tt.button(Iconc.players + "", MI2UVars.textbtoggle, () -> m.drawLabel = !m.drawLabel).update(b -> b.setChecked(m.drawLabel)).size(48f);
            });
        }).growX();
    }

    @Override
    public void initSettings(){
        super.initSettings();
        settings.add(new FieldSettingEntry(SettingType.Int, mindowName + ".size", s -> {
            return Strings.canParseInt(s) && Strings.parseInt(s) >= 100 && Strings.parseInt(s) <= 600;
        }, "@settings.mindowMap.size", s -> rebuild()));
    }

    @Override
    public boolean loadUISettingsRaw(){
        if(!super.loadUISettingsRaw()) return false;
        int size = MI2USettings.getInt(mindowName + ".size");
        m.setMapSize(size);
        rebuild();
        return true;
    }
    
    public static class Minimap2 extends Table{
        protected Element map;
        public boolean drawLabel = true;
        private static final float baseSize = 16f;
        public float zoom = 4;

        public Minimap2(float size){
            float margin = 5f;
            this.touchable = Touchable.enabled;
            map = new Element(){
                {
                    setSize(Scl.scl(size));
                }
    
                @Override
                public void act(float delta){
                    setPosition(Scl.scl(margin), Scl.scl(margin));
    
                    super.act(delta);
                }
    
                @Override
                public void draw(){
                    if(renderer.minimap.getRegion() == null) return;
                    if(!clipBegin()) return;
                    
                    Draw.reset();
                    Draw.rect(getRegion(), x + width / 2f, y + height / 2f, width, height);
    
                    if(renderer.minimap.getTexture() != null){
                        Draw.alpha(parentAlpha);
                        drawEntities(x, y, width, height, 0.75f, drawLabel);
                    }
    
                    clipEnd();
                }
            };

            margin(margin);

            addListener(new InputListener(){
                @Override
                public boolean scrolled(InputEvent event, float x, float y, float amountx, float amounty){
                    zoomBy(amounty);
                    return true;
                }
            });
    
            addListener(new ClickListener(){
                {
                    tapSquareSize = Scl.scl(11f);
                }
    
                @Override
                public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button){
                    if(inTapSquare()){
                        super.touchUp(event, x, y, pointer, button);
                    }else{
                        pressed = false;
                        pressedPointer = -1;
                        pressedButton = null;
                        cancelled = false;
                    }
                }
    
                @Override
                public void touchDragged(InputEvent event, float x, float y, int pointer){
                    if(!inTapSquare(x, y)){
                        invalidateTapSquare();
                    }
                    super.touchDragged(event, x, y, pointer);
    
                    if(mobile){
                        float max = Math.min(world.width(), world.height()) / 16f / 2f;
                        setZoom(1f + y / height * (max - 1f));
                    }
                }
    
                @Override
                public void clicked(InputEvent event, float x, float y){
                    if(control.input instanceof DesktopInput || control.input instanceof InputOverwrite){
                        try{
                            float sz = baseSize * zoom;
                            float dx = (Core.camera.position.x / tilesize);
                            float dy = (Core.camera.position.y / tilesize);
                            dx = (2 * sz) <= world.width() ? Mathf.clamp(dx, sz, world.width() - sz) : world.width() / 2;
                            dy = (2 * sz) <= world.height() ? Mathf.clamp(dy, sz, world.height() - sz) : world.height() / 2;
                            if(control.input instanceof DesktopInput inp){
                                inp.panning = true;
                                Core.camera.position.set(
                                        ((x / width - 0.5f) * 2f * sz * tilesize + dx * tilesize),
                                        ((y / height - 0.5f) * 2f * sz * tilesize + dy * tilesize));
                            }else if(control.input instanceof InputOverwrite ino){
                                ino.pan(true, MI2UTmp.v1.set((x / width - 0.5f) * 2f * sz * tilesize + dx * tilesize, (y / height - 0.5f) * 2f * sz * tilesize + dy * tilesize));
                            }
                        }catch(Exception e){
                            Log.err("Minimap", e);
                        }
                    }else{
                        ui.minimapfrag.toggle();
                    }
                }
            });
    
            update(() -> {
                Element e = Core.scene.hit(Core.input.mouseX(), Core.input.mouseY(), true);
                if(e != null && e.isDescendantOf(this)){
                    requestScroll();
                }else if(hasScroll()){
                    Core.scene.setScrollFocus(null);
                }
            });

            add(map).size(size);
        }

        public void setMapSize(float size){
            clearChildren();
            map.setSize(Scl.scl(size));
            add(map).size(size);
        }

        /** these methods below are replacing vanilla minimap renderer method*/
        public void zoomBy(float amount){
            zoom += amount;
            setZoom(zoom);
        }

        public void setZoom(float amount){
            zoom = Mathf.clamp(amount, 1f, Math.max(world.width(), world.height()) / baseSize / 2f);
        }

        public void drawEntities(float x, float y, float w, float h, float scaling, boolean withLabels){
            float sz = baseSize * zoom;
            float dx = (Core.camera.position.x / tilesize);
            float dy = (Core.camera.position.y / tilesize);
            dx = (2 * sz) <= world.width() ? Mathf.clamp(dx, sz, world.width() - sz) : world.width() / 2;
            dy = (2 * sz) <= world.height() ? Mathf.clamp(dy, sz, world.height() - sz) : world.height() / 2;

            Rect rect = MI2UTmp.r1.set((dx - sz) * tilesize, (dy - sz) * tilesize, sz * 2 * tilesize, sz * 2 * tilesize);

            //draw a linerect of view area
            Lines.stroke(1f, new Color(1f, 1f, 1f, 0.5f));
            float cx = withLabels ? (Core.camera.position.x - rect.x) / rect.width * w : Core.camera.position.x / (world.width() * tilesize) * w;
            float cy = withLabels ? (Core.camera.position.y - rect.y) / rect.width * h : Core.camera.position.y / (world.height() * tilesize) * h;
            Lines.rect(x + cx - Core.graphics.getWidth() / rect.width * w / renderer.getScale() / 2f,
                    y + cy - Core.graphics.getHeight() / rect.width * h / renderer.getScale() / 2f,
                    Core.graphics.getWidth() / rect.width * w / renderer.getScale() ,
                    Core.graphics.getHeight() / rect.width * h / renderer.getScale());
            Draw.color();
            //just render unit group
            Groups.unit.each(unit -> {
                float rx = (unit.x - rect.x) / rect.width * w;
                float ry = (unit.y - rect.y) / rect.width * h;

                float scale = Scl.scl(1f) / 2f * scaling * 32f;
                var region = unit.icon();
                //color difference between block and unit in setting
                Draw.mixcol(new Color(unit.team().color.r * 0.9f, unit.team().color.g * 0.9f, unit.team().color.b * 0.9f, 1f), 1f);
                Draw.rect(region, x + rx, y + ry, scale, scale * (float)region.height / region.width, unit.rotation() - 90);
                Draw.reset();
            });

            //display labels
            if(withLabels){
                for(Player player : Groups.player){
                    if(!player.dead()){
                        //float rx = player.x / (world.width() * tilesize) * w;
                        //float ry = player.y / (world.height() * tilesize) * h;
                        float rx = (player.x - rect.x) / rect.width * w;
                        float ry = (player.y - rect.y) / rect.width * h;

                        drawLabel(x + rx, y + ry, player.name, player.team().color);
                    }
                }
            }

            Draw.reset();
        }

        public @Nullable TextureRegion getRegion(){
            //TODO get texture, region in minimaprenderer
            var texture = getTextureRef();
            if(texture == null) return null;
            var region = getRegionRef();
            if(texture == null) return null;
            //2 * sz = map width/height in tiles
            float sz = baseSize * zoom;
            float dx = (Core.camera.position.x / tilesize);
            float dy = (Core.camera.position.y / tilesize);
            dx = (2 * sz) <= world.width() ? Mathf.clamp(dx, sz, world.width() - sz) : world.width() / 2;
            dy = (2 * sz) <= world.height() ? Mathf.clamp(dy, sz, world.height() - sz) : world.height() / 2;
            float invTexWidth = 1f / texture.width;
            float invTexHeight = 1f / texture.height;
            float x = dx - sz, y = world.height() - dy - sz, width = sz * 2, height = sz * 2;
            region.set(x * invTexWidth, y * invTexHeight, (x + width) * invTexWidth, (y + height) * invTexHeight);
            return region;
        }

        public void drawLabel(float x, float y, String text, Color color){
            Font font = Fonts.outline;
            GlyphLayout l = Pools.obtain(GlyphLayout.class, GlyphLayout::new);
            boolean ints = font.usesIntegerPositions();
            font.getData().setScale(1 / 1.5f / Scl.scl(1f));
            font.setUseIntegerPositions(false);

            l.setText(font, text, color, 90f, Align.left, true);
            float yOffset = 20f;
            float margin = 3f;

            Draw.color(0f, 0f, 0f, 0.2f);
            Fill.rect(x, y + yOffset - l.height/2f, l.width + margin, l.height + margin);
            Draw.color();
            font.setColor(color);
            font.draw(text, x - l.width/2f, y + yOffset, 90f, Align.left, true);
            font.setUseIntegerPositions(ints);
            font.getData().setScale(1f);
            font.setColor(Color.white);
            Pools.free(l);
        }

        public static Texture getTextureRef(){
            Texture texture;
            try{
                texture = Reflect.get(renderer.minimap, "texture");
            }catch (Exception e){
                return null;
            }
            if(texture == null) return null;
            return texture;
        }

        public static TextureRegion getRegionRef(){
            TextureRegion region;
            try{
                if(getTextureRef() == null) return null;
                region = Reflect.get(renderer.minimap, "region");
            }catch (Exception e){
                return null;
            }
            if(region == null) return null;
            return region;
        }

    }
}
