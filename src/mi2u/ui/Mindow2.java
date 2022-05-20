package mi2u.ui;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.input.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.*;
import arc.scene.event.*;
import arc.scene.style.*;
import arc.scene.ui.*;
import arc.scene.ui.Label.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mi2u.MI2UTmp;
import mi2u.game.MI2UEvents;
import mi2u.io.*;
import mi2u.io.MI2USettings.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;

import static mi2u.MI2UVars.*;
/**  
 * Mindow2 is a dragable Table that partly works like a window. 
 * titleText is text shown on titleBar, set in constructor.
 * mindowName is inner name, used for window-specific settings, set in overrided constructor.
 * helpInfo is text shown in window-specific help dialog, set in constructor.
 * cont is a container for user items.
 * settings is a SettingEntry seq.
 * <p>
 * {@code setupCont(Table cont)}for cont rebuild, should be overrided.<p>
 * {@code initSettings()}for customize settings, should start with settings.clear()
 * @author BlackDeluxeCat
 */

public class Mindow2 extends Table{
    @Nullable public static Mindow2 currTopmost = null;
    public static LabelStyle titleStyleNormal, titleStyleSnapped;
    public static Drawable titleBarbgNormal, titleBarbgSnapped, white;

    public float fromx = 0, fromy = 0, curx = 0, cury = 0, titleScale = 1f;
    public boolean topmost = false, minimized = false, closable = true;
    public String titleText = "", helpInfo = "", mindowName;
    protected Table titleBar = new Table();
    protected Table cont = new Table();
    protected Seq<SettingEntry> settings = new Seq<>();
    protected Interval interval = new Interval(2);
    @Nullable public Element aboveSnap; public int edgesnap = -1;

    public Mindow2(String title){
        init();

        Events.on(MI2UEvents.FinishSettingInitEvent.class, e -> {
            initSettings();
            loadUISettings();
        });

        Events.on(ResizeEvent.class, e -> {
            loadUISettings();
        });

        titleText = title;
        registerName();
        rebuild();
    }

    public Mindow2(String title, String help){
        this(title);
        helpInfo = help;
    }

    public void init(){}

    public void rebuild(){
        clear();
        setupTitle();
        row();
        if(!minimized){
            cont.setBackground(Styles.black3);
            add(cont);
            setupCont(cont);
        }
    }

    /** called when rebuild Mindow2, should be overrided */
    public void setupCont(Table cont){}

    /** called when click minimize-button, can be overrided */
    public void minimize(){
        rebuild();
    }

    /** called when click close-button, can be overrided */
    public void close(){
        remove();
    }

    public void setupTitle(){
        titleBar.clear();
        var title = new Label(titleText);
        title.name = "Mindow2Title";
        title.setAlignment(Align.left);
        title.addListener(new InputListener(){
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button){
                fromx = x;
                fromy = y;
                return true;
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer){
                Vec2 v = localToStageCoordinates(MI2UTmp.v1.set(x, y));
                Element hit = Core.scene.hit(v.x + title.x + title.parent.x, v.y + title.y + title.parent.y, false);
                if(hit != null && hit.name == "Mindow2Title" && !hit.isDescendantOf(Mindow2.this)){
                    aboveSnap = hit.parent.parent;
                    return;
                }
                aboveSnap = null;
                curx = v.x - fromx;
                cury = v.y - fromy;
            }
        });
        titleBar.add(title).pad(0, 1, 0, 1).growX();

        titleBar.button("" + Iconc.info, textb, () -> {
            showHelp();
        }).size(titleButtonSize);

        titleBar.button("" + Iconc.settings, textb, () -> {
            showSettings();
        }).size(titleButtonSize);

        titleBar.button("" + Iconc.lock, textbtoggle, () -> {
            topmost = !topmost;
            if(topmost){
                currTopmost = this;
            }else{
                if(currTopmost == this) currTopmost = null;
            }
            rebuild();
        }).size(titleButtonSize).update(b -> {
            topmost = currTopmost == this;
            b.setChecked(topmost);
        });

        titleBar.button("-", textbtoggle, () -> {
            minimized = !minimized;
            if(minimized){
                cury += cont.getHeight();
            }else{
                cury -= cont.getHeight();
            }
            minimize();
        }).size(titleButtonSize).update(b -> {
            b.setChecked(minimized);
        });

        titleBar.button("X", textb, () -> {
            close();
        }).size(titleButtonSize).update(b -> {
            b.setDisabled(!closable);
        });

        addListener(new InputListener(){
            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Element fromActor){
                if(fromActor != null && fromActor.isDescendantOf(Mindow2.this)) return;
                titleScale = 1f;
                interval.get(1, 1);
            }
            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Element toActor){
                if(toActor != null && toActor.isDescendantOf(Mindow2.this)) return;
                titleScale = 0f;
                interval.get(0, 1);
            }
        });

        titleBar.setTransform(true);
        interval.reset(0, 1);
        interval.reset(1, 1);
        titleBar.update(() -> {
            cont.touchable = Touchable.enabled;
            //TODO add a abovesnap listener
            //title.setStyle(aboveSnap == null ? titleStyleNormal : titleStyleSnapped);
            titleBar.setBackground(aboveSnap == null ? titleBarbgNormal : titleBarbgSnapped);
            //don't collapse when minimized or empty container
            if(minimized || !MI2USettings.getBool(mindowName + ".autoHideTitle", false) || cont.getPrefHeight() < 20f || !cont.visible) titleScale = 1f;
            if((interval.check(0, 180) && titleBar.scaleY > 0.95f) || (interval.check(1, 15) && titleBar.scaleY < 0.95f)){
                titleBar.toFront();
                titleBar.setScale(1, Mathf.lerpDelta(titleBar.scaleY, titleScale, 0.3f));
                //titleBar.keepInStage();
                //titleBar.invalidateHierarchy();
                //titleBar.pack();
                titleBar.touchable = Touchable.enabled;
            }else if(titleBar.scaleY < 0.95f){
                titleBar.touchable = Touchable.disabled;
            }
            edgesnap = MI2USettings.getInt(mindowName + ".edgesnap", -1);
            if(this == currTopmost || shouldTopMost()) setZIndex(1000);

            if(aboveSnap != null){
                curx = aboveSnap.x;
                cury = aboveSnap.y - getRealHeight();
                setPosition(curx, cury);
                keepInStage();
            }else if(edgesnap != 4 && hasParent()){
                edgeSnap(edgesnap);
                setPosition(curx, cury);
            }else{
                setPosition(curx, cury);
                keepInStage();
            }
            invalidateHierarchy();
            pack();
        });
        add(titleBar).growX();
    }

    protected void edgeSnap(int s){
        switch(s){
            case 6://lefttop
                curx = 0;
                cury = parent.getHeight() - getRealHeight();
                break;
            case 7://top
                //curx = (parent.getWidth() - getWidth())/2f;
                cury = (parent.getHeight() -getRealHeight());
                break;
            case 8://righttop
                curx = (parent.getWidth() - getWidth());
                cury = (parent.getHeight() -getRealHeight());
                break;
            case 5://right
                curx = (parent.getWidth() - getWidth());
                //cury = (parent.getHeight() -getRealHeight())/2f;
                break;
            case 2://rightbottom
                curx = (parent.getWidth() - getWidth());
                cury = 0;
                break;
            case 1://bottom
                //curx = (parent.getWidth() - getWidth())/2f;
                cury = 0;
                break;
            case 0://leftbottom
                curx = 0;
                cury = 0;
                break;
            case 3://left
                curx = 0;
                //cury = (parent.getHeight() -getRealHeight())/2f;
                break;
        }
    }

    public boolean addTo(Group newParent){
        if(newParent == null){
            return !this.remove();
        }
        this.remove();
        newParent.addChild(this);
        return true;
    }

    public float getRealHeight(){
        return getHeight() - titleBar.getHeight() *  (1 - titleBar.scaleY);
    }

    public boolean shouldTopMost(){
        return (topmost || (aboveSnap !=null && aboveSnap != this && aboveSnap instanceof Mindow2 m && m.shouldTopMost()));
    }
    
    public void showHelp(){
        new BaseDialog("@mindow2.helpInfoTitle"){
            {
                addCloseButton();
                this.cont.pane(t -> {
                    t.add(helpInfo).padBottom(60f).left().width(Core.graphics.getWidth() / 1.5f).get().setWrap(true);
                    t.row();
                    t.add("@mindow2.uiHelp").left().width(Core.graphics.getWidth() / 1.5f).get().setWrap(true);
                });
                show();
            }
        };
    }

    /** Settings shoulded be set in Seq: settings, will be shown and configurable in SettingsDialog 
     * UISetting will be shown to, but not configurable
    */
    public void showSettings(){
        new BaseDialog("@mindow2.settings.title"){
            {
                this.buttons.add("@mindow2.settingHelp").width(Math.min(600, Core.graphics.getWidth())).get().setWrap(true);
                this.buttons.row();
                addCloseButton();
                this.cont.pane(t -> {
                    t.add(mindowName != null && !mindowName.equals("") ? Core.bundle.format("mindow2.settings.curMindowName") + mindowName: "@mindow2.settings.noMindowNameWarning").fontScale(1.2f).get().setAlignment(Align.center);
                    t.row();
                    settings.each(st -> {
                        t.table(tt -> st.build(tt)).width(Math.min(600, Core.graphics.getWidth())).left();
                        t.row();
                    });
                }).grow();

                this.cont.row();

                this.cont.table(tt -> {
                    tt.button("@mindow2.settings.reloadUI", textb, () -> {
                        loadUISettings();
                    }).width(100).get().getLabel().setColor(1, 1, 0, 1);

                    tt.button("@mindow2.settings.cacheUI", textb, () -> {
                        saveUISettings();
                    }).width(100).get().getLabel().setColor(0, 0, 1, 1);
                }).self(c -> {
                    c.width(Math.min(530, Core.graphics.getWidth()));
                });
                show();
            }
        };
    }

    /** can be overrided, should use super.initSettings(), called in rebuild() */
    public void initSettings(){
        settings.clear();
        if(mindowName == null || mindowName.equals("")) return;

        settings.add(new MindowUIGroupEntry(mindowName + ".Mindow", ""));

        settings.add(new FieldEntry(mindowName + ".abovesnapTarget", "@settings.mindow.abovesnapTarget", "", null, s -> mindow2s.contains(mi2 -> mi2.mindowName.equals(s)) || s.equals("null"), null));
        settings.add(new CheckEntry(mindowName + ".autoHideTitle", "@settings.mindow.autoHideTitle", false, null));
    }

    /** Override this method for custom UI settings load
     * rebuild() called once finished loading
     */
    public boolean loadUISettingsRaw(){
        //it is a no-named mindow2, no settings can be loaded.
        if(mindowName == null || mindowName.equals("")) return false;
        minimized = MI2USettings.getBool(mindowName + ".minimized");
        topmost = MI2USettings.getBool(mindowName + ".topmost");
        if(topmost) currTopmost = this;
        edgesnap = MI2USettings.getInt(mindowName + ".edgesnap", -1);
        curx = (float)MI2USettings.getInt(mindowName + ".curx");
        cury = (float)MI2USettings.getInt(mindowName + ".cury");
        if(MI2USettings.getStr(mindowName + ".abovesnapTarget").equals("null")){
            aboveSnap = null;
        }else{
            mindow2s.each(m -> {
                if(m.mindowName.equals(MI2USettings.getStr(mindowName + ".abovesnapTarget"))){
                    aboveSnap = m;
                    Log.info(mindowName + " snaps to " + m.mindowName);
                }
            });
        }
        return true;
    }

    public void loadUISettings(){
        loadUISettingsRaw();
        rebuild();
    }

    /** Override this method for custom UI settings save
     */
    public boolean saveUISettings(){
        //it is a not-named mindow2, no settings can be saved.
        if(mindowName == null || mindowName.equals("")) return false;
        MI2USettings.putBool(mindowName + ".minimized", minimized);
        MI2USettings.putBool(mindowName + ".topmost", topmost);
        MI2USettings.putInt(mindowName + ".edgesnap", edgesnap);
        //edgesnap will change curx cury, so xy shouldn't be saved when edgesnapping.
        if(edgesnap != 1 && edgesnap != 5){
            MI2USettings.putInt(mindowName + ".cury", (int)cury);
        }
        if(edgesnap != 3 && edgesnap != 7){
            MI2USettings.putInt(mindowName + ".curx", (int)curx);
        }
        return true;
    }

    public boolean registerName(){
        if(mindowName != null && !mindowName.equals("") && !mindow2s.contains(m -> m.mindowName.equals(this.mindowName))){
            mindow2s.add(this);
            return true;
        }
        return false;
    }

    public static void initMindowStyles(){
        var whiteui = (TextureRegionDrawable)Tex.whiteui;
        titleStyleNormal = new LabelStyle(Fonts.def, new Color(0.8f,0.9f,1f,1f));
        //titleStyleNormal.background = whiteui.tint(1f, 0.1f, 0.2f, 0.8f);
        titleStyleSnapped = new LabelStyle(Fonts.def, new Color(0.1f,0.6f,0.6f,1f));
        //titleStyleSnapped.background = whiteui.tint(1f, 0.1f, 0.2f, 0.2f);
        titleBarbgNormal = whiteui.tint(1f, 0.1f, 0.2f, 0.8f);
        titleBarbgSnapped = whiteui.tint(1f, 0.1f, 0.2f, 0.2f);
        white = whiteui.tint(1f, 1f, 1f, 1f);
    }

    public class MindowUIGroupEntry extends SettingGroupEntry{
        SingleEntry entry1 = new SingleEntry(mindowName + ".minimized", "");
        SingleEntry entry2 = new SingleEntry(mindowName + ".topmost", "");
        SingleEntry entry3 = new SingleEntry(mindowName + ".curx", "");
        SingleEntry entry4 = new SingleEntry(mindowName + ".cury", "");
        SingleEntry entry5 = new SingleEntry(mindowName + ".edgesnap", "");
        Table buildTarget;
        public MindowUIGroupEntry(String name, String help) {
            super(name, help);
            Events.on(ResizeEvent.class, e -> {
                if(buildTarget != null && buildTarget.hasParent()) {
                    build(buildTarget);
                }else if(buildTarget != null) buildTarget = null;
            });

            builder = t -> {
                buildTarget = t;
                t.clear();
                t.defaults().pad(0f, 15f, 0f, 15f);
                t.margin(10f);
                t.background(Styles.flatDown);
                t.stack(new Element(){
                            @Override
                            public void draw(){
                                super.draw();
                                Draw.color(Color.darkGray);
                                Draw.alpha(parentAlpha);
                                float divw = this.getWidth()/3f, divh = this.getHeight()/3f;
                                Fill.rect(x + this.getWidth()/2f, y + this.getHeight()/2f, this.getWidth(), this.getHeight());
                                Draw.color(Color.olive);
                                Draw.alpha(parentAlpha);
                                Fill.rect(x + this.getWidth() /6f + Mathf.mod(edgesnap, 3) * divw, y + this.getHeight() /6f + (edgesnap/3) * divh, divw, divh);
                                Draw.reset();
                            }
                            {
                                Element el = this;
                                addListener(new InputListener(){
                                    @Override
                                    public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button){
                                        edgesnap = Mathf.floor(x/el.getWidth()*3f) + Mathf.floor(y/el.getHeight()*3f)*3;
                                        MI2USettings.putInt(mindowName + ".edgesnap", edgesnap);
                                        return super.touchDown(event, x, y, pointer, button);
                                    }
                                });
                            }
                        }, new Table(){{
                            this.add(new Element(){
                                {this.touchable = Touchable.disabled;}
                                @Override
                                public void draw() {
                                    super.draw();
                                    Draw.color(Color.grays(0.1f));
                                    Draw.alpha(parentAlpha * 0.8f);
                                    Fill.rect(x + this.getWidth()/2f, y + this.getHeight()/2f, this.getWidth(), this.getHeight());

                                    mindow2s.each(mind -> {
                                        if(mind.parent != Mindow2.this.parent) return;
                                        Draw.color(mind == Mindow2.this ? Color.coral : mind == aboveSnap ? Color.royal : Color.grays(0.4f));
                                        Draw.alpha(0.8f * parentAlpha * 0.8f);
                                        float mindw = (mind.getWidth()/Core.graphics.getWidth())*this.getWidth(),
                                                mindh = (mind.getHeight()/Core.graphics.getHeight())*this.getHeight();
                                        float mindx = x + (mind.x/Core.graphics.getWidth())*this.getWidth() + mindw/2f, mindy = y + (mind.y/Core.graphics.getHeight())*this.getHeight() + mindh/2f;
                                        Fill.rect(mindx, mindy, mindw, mindh);
                                        Draw.reset();
                                    });
                                }
                            }).self(c -> c.pad(10f).size(200f*Core.graphics.getWidth()/Core.graphics.getHeight(), 200f));
                        }}
                ).fill().size(200f*Core.graphics.getWidth()/Core.graphics.getHeight() + 20f, 220f);
                t.row();
                t.table(tt -> {
                    entry1.build(tt);
                    tt.row();
                    entry2.build(tt);
                    tt.row();
                    entry3.build(tt);
                    tt.row();
                    entry4.build(tt);
                    tt.row();
                    entry5.build(tt);
                });
            };
        }
    }
}
