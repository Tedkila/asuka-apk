package fr.maison.asuka;

/** Pure local policy, independent of camera hardware. */
final class PresencePolicy {
    static boolean sleeping(int minute, int start, int end) {
        if (start == end) return false;
        return start < end ? minute >= start && minute < end : minute >= start || minute < end;
    }
    static int time(String value) {
        if (!value.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]")) throw new IllegalArgumentException("Heure HH:MM attendue");
        return Integer.parseInt(value.substring(0,2))*60 + Integer.parseInt(value.substring(3));
    }
    static double cosine(float[] a, float[] b) {
        if (a.length != b.length || a.length == 0) return -1;
        double dot=0, x=0, y=0;
        for (int i=0;i<a.length;i++) { dot+=a[i]*b[i]; x+=a[i]*a[i]; y+=b[i]*b[i]; }
        return x == 0 || y == 0 ? -1 : dot / Math.sqrt(x*y);
    }
}
