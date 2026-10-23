# Camera validation archives

The root-level results, screenshots, client logs and `CameraCheck.java` are the
original cloud-client checks from `c702e87`, preserved byte-for-byte. See
[results-full.txt](results-full.txt) and [results-readback.txt](results-readback.txt).
That attempt's front view was limited by floor collision; its other observations
and remaining gaps are described in [VIDEO_SETTINGS.md](../../VIDEO_SETTINGS.md).

The independent [local Docker follow-up](local-docker-rerun/README.md) retains
both local full/readback attempts, 35 screenshots, the refined harness and the
harness-free clean build and full client smoke. Its level front view confirms
the 4-to-8-block distance change. Cloud and local results are not substituted
for each other. No temporary harness remains in shipping sources or jars.
