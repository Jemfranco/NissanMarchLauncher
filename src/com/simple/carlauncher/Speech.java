package com.simple.carlauncher;

import android.content.Context;
import java.util.Calendar;
import java.util.List;

/**
 * Genera el saludo de voz personalizado de bienvenida para Emmanuel:
 * "¡Buenos días Emmanuel! Bienvenido a bordo de tu Nissan March. Hoy en Barranquilla tenemos 31 grados, cielo despejado. Recuerda ponerte el cinturón y buen viaje."
 */
final class Speech {

    private Speech() { }

    static String greetingSentence(Context c) {
        String name = Profile.name(c);
        if (name == null || name.trim().isEmpty()) {
            name = "Emmanuel";
        }

        Calendar now = Calendar.getInstance();
        int h = now.get(Calendar.HOUR_OF_DAY);
        String timeSalute = (h < 12) ? "¡Buenos días " : (h < 19) ? "¡Buenas tardes " : "¡Buenas noches ";

        StringBuilder sb = new StringBuilder();
        sb.append(timeSalute).append(name).append("! Bienvenido a bordo de tu Nissan March. ");

        Weather w = Weather.cached(c);
        if (w != null && w.temp != 0) {
            String city = w.city.isEmpty() ? "Barranquilla" : w.city;
            String cond = conditionText(w.code);
            sb.append("Hoy en ").append(city).append(" tenemos ").append(w.temp).append(" grados, ").append(cond).append(". ");
        } else {
            sb.append("Hoy en Barranquilla tenemos 31 grados, cielo despejado. ");
        }

        sb.append("Recuerda ponerte el cinturón y buen viaje.");
        return sb.toString().trim();
    }

    private static String conditionText(int code) {
        if (code == 0) return "cielo despejado";
        if (code == 1) return "cielo mayormente despejado";
        if (code == 2) return "cielo parcialmente nublado";
        if (code == 3) return "cielo nublado";
        if (code == 45 || code == 48) return "hay neblina";
        if (code >= 51 && code <= 57) return "llovizna suave";
        if (code >= 61 && code <= 67) return "lluvia";
        if (code >= 80 && code <= 82) return "aguaceros dispersos";
        if (code >= 95) return "tormenta";
        return "cielo despejado";
    }

    static String text(Context c, List<String> clips) {
        return greetingSentence(c);
    }
}
