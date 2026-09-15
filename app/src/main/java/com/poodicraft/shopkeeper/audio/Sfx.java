package com.poodicraft.shopkeeper.audio;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

/**
 * Tiny synthesised sound bank.
 *
 * <p>Waveforms are generated once into static {@link AudioTrack} buffers, so the game
 * ships no audio assets. Every call is defensive: if audio is unavailable the game
 * carries on silently.
 */
public final class Sfx {

    public static final int SOUND_COIN = 0;
    public static final int SOUND_TAP = 1;
    public static final int SOUND_LEVEL = 2;
    public static final int SOUND_DENY = 3;
    public static final int SOUND_RESTOCK = 4;
    private static final int SOUND_COUNT = 5;

    private static final int SAMPLE_RATE = 22050;

    private final AudioTrack[] tracks = new AudioTrack[SOUND_COUNT];
    private boolean enabled = true;

    public Sfx() {
        tracks[SOUND_COIN] = make(coin());
        tracks[SOUND_TAP] = make(blip(760f, 0.06f, 0.35f));
        tracks[SOUND_LEVEL] = make(arpeggio());
        tracks[SOUND_DENY] = make(blip(180f, 0.16f, 0.4f));
        tracks[SOUND_RESTOCK] = make(blip(420f, 0.1f, 0.3f));
    }

    public void setEnabled(boolean value) { enabled = value; }

    public boolean isEnabled() { return enabled; }

    public void play(int sound) {
        if (!enabled || sound < 0 || sound >= tracks.length) return;
        AudioTrack track = tracks[sound];
        if (track == null) return;
        try {
            if (track.getPlayState() != AudioTrack.PLAYSTATE_STOPPED) track.stop();
            track.reloadStaticData();
            track.play();
        } catch (RuntimeException ignored) {
            // A busy or torn-down track is never worth interrupting the game for.
        }
    }

    public void release() {
        for (int i = 0; i < tracks.length; i++) {
            if (tracks[i] == null) continue;
            try {
                tracks[i].stop();
                tracks[i].release();
            } catch (RuntimeException ignored) {
                // Already gone.
            }
            tracks[i] = null;
        }
    }

    private AudioTrack make(short[] samples) {
        try {
            byte[] bytes = new byte[samples.length * 2];
            for (int i = 0; i < samples.length; i++) {
                bytes[i * 2] = (byte) (samples[i] & 0xFF);
                bytes[i * 2 + 1] = (byte) ((samples[i] >> 8) & 0xFF);
            }
            AudioTrack track = new AudioTrack(AudioManager.STREAM_MUSIC, SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    bytes.length, AudioTrack.MODE_STATIC);
            track.write(bytes, 0, bytes.length);
            return track;
        } catch (RuntimeException e) {
            // Devices with no usable output simply get a silent game.
            return null;
        }
    }

    /** Two quick bell tones, like a till drawer closing. */
    private short[] coin() {
        int length = (int) (SAMPLE_RATE * 0.28f);
        short[] out = new short[length];
        for (int i = 0; i < length; i++) {
            float t = (float) i / SAMPLE_RATE;
            float envelope = (float) Math.exp(-t * 11f);
            float a = (float) Math.sin(2 * Math.PI * 1180 * t);
            float b = (float) Math.sin(2 * Math.PI * 1560 * (t - 0.06f));
            float second = t > 0.06f ? b * (float) Math.exp(-(t - 0.06f) * 13f) : 0f;
            float v = (a * envelope * 0.55f + second * 0.45f) * 0.5f;
            out[i] = clip(v);
        }
        return out;
    }

    private short[] blip(float frequency, float duration, float volume) {
        int length = (int) (SAMPLE_RATE * duration);
        short[] out = new short[length];
        for (int i = 0; i < length; i++) {
            float t = (float) i / SAMPLE_RATE;
            float envelope = (float) Math.exp(-t / Math.max(0.01f, duration * 0.35f));
            float v = (float) Math.sin(2 * Math.PI * frequency * t) * envelope * volume;
            out[i] = clip(v);
        }
        return out;
    }

    /** Rising three-note flourish for a level-up. */
    private short[] arpeggio() {
        float[] notes = {523.25f, 659.25f, 783.99f, 1046.5f};
        float noteLength = 0.11f;
        int length = (int) (SAMPLE_RATE * noteLength * notes.length);
        short[] out = new short[length];
        for (int i = 0; i < length; i++) {
            float t = (float) i / SAMPLE_RATE;
            int note = Math.min(notes.length - 1, (int) (t / noteLength));
            float local = t - note * noteLength;
            float envelope = (float) Math.exp(-local * 9f);
            float v = (float) Math.sin(2 * Math.PI * notes[note] * local) * envelope * 0.4f;
            out[i] = clip(v);
        }
        return out;
    }

    private static short clip(float v) {
        if (v > 1f) v = 1f;
        if (v < -1f) v = -1f;
        return (short) (v * 32000f);
    }
}
