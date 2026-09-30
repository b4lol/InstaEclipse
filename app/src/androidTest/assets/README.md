Synthetic audio extraction fixtures (no third-party or account data).

Regenerate with FFmpeg:

```sh
ffmpeg -f lavfi -i color=c=black:s=32x32:r=10 -f lavfi -i sine=frequency=440:sample_rate=44100 -t 0.3 -c:v mpeg4 -c:a aac -y audio-extraction.mp4
ffmpeg -i audio-extraction.mp4 -an -c:v copy -y video-only.mp4
```

`AudioExtractionTest` checks that the muxed output contains AAC samples and no
video track, and that a source without audio is rejected.
