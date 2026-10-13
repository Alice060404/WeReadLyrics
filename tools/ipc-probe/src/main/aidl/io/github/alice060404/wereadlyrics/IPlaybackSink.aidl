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
}
