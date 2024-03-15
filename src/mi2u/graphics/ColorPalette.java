package mi2u.graphics;

import java.util.HashMap;

import arc.graphics.Color;

public class ColorPalette {

    private static final HashMap<String, Color> palette = new HashMap<>(){{
        put("duo", Color.gray);
        put("scatter", Color.purple);
        put("scorch", Color.red);
        put("hail", Color.darkGray);
        put("wave", Color.navy);
        put("lancer", Color.cyan);
        put("arc", Color.white);
        put("parallex",Color.coral);
        put("swarmer", Color.maroon);
        put("salvo",Color.gold);
        put("segment",Color.green);
        put("tsunami",Color.blue);
        put("fuse",Color.pink);
        put("ripple",Color.magenta);
        put("cyclone",Color.acid);
        put("foreshadow",Color.salmon);
        put("spectre",Color.teal);
        put("meltdown",Color.brick);
    }};

    public static Color getColorForTurretName(String name){
        if(palette.containsKey(name)){
            return palette.get(name);
        }else{
            return Color.white;
        }
    }
}