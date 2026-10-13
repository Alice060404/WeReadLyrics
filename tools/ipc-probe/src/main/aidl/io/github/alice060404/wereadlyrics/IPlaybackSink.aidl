package io.github.alice060404.wereadlyrics;

interface IPlaybackSink {
    oneway void publishState(
        long sourceInstanceId,
        long sessionId,
        long sequence,
        int status,
        String text
    );
}
