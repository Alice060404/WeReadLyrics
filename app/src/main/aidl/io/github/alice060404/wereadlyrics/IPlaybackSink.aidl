package io.github.alice060404.wereadlyrics;

import android.os.IBinder;

interface IPlaybackSink {
    oneway void publishState(
        long sourceInstanceId,
        long sessionId,
        long sequence,
        int status,
        String text,
        IBinder sourceToken
    );

    // Keep publishState first for modules already loaded in the host process.
    oneway void publishPositionedState(
        long sourceInstanceId,
        long sessionId,
        long sequence,
        int status,
        String text,
        IBinder sourceToken,
        int characterOffset
    );
}
