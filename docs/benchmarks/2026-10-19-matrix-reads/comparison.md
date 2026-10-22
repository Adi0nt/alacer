# render.avoidMatrixReads — measurement and in-process A/B, 200 mobs

Generated from the `runs/` summaries. Segments A, B, B, A per launch; "vs a2" compares the B
segments with the second A segment only (the first segment of each phase is about 3% slower
regardless of the option). The driver mode is judged from the A segments (process CPU above 1.6
cores and swap below 0.15 ms = threaded): with the option on, B moves the remaining driver wait
into the swap, which would otherwise be misread as the single-threaded mode.

Columns: `glerr` = `gl_error_check`, `cam` = `camera_setup`, `frus` = `frustum`, `cull` =
`frustum_culler` (all ms per frame, frame-weighted means of a1+a2 and b1+b2); `cpu` = client
thread CPU ms per frame.

## Where the wait went (toggle `render.throttleGlErrorChecks`, `gsr-m1`, `gsr-m2`)

```
gsr-m1 static threaded cores 1.83  frame 2.612->2.322 (-11.1%)  vs a2 -4.8%  glerr 0.907->0.005  render 1.531->2.153  cam 0.097->0.830  frus 0.011->0.011  cull 0.004->0.003  swap 0.078->0.055  cpu 2.424->2.242
gsr-m1 pan    threaded cores 1.65  frame 3.739->4.079 (+9.1%)  vs a2 +11.6%  glerr 1.304->0.011  render 2.232->3.714  cam 0.095->1.297  frus 0.010->0.012  cull 0.003->0.004  swap 0.088->0.115  cpu 3.392->3.679
gsr-m2 static threaded cores 1.80  frame 2.646->2.556 (-3.4%)  vs a2 +1.6%  glerr 0.863->0.004  render 1.609->2.345  cam 0.096->0.846  frus 0.011->0.010  cull 0.006->0.004  swap 0.075->0.077  cpu 2.500->2.420
gsr-m2 pan    threaded cores 1.67  frame 3.996->3.690 (-7.7%)  vs a2 -5.9%  glerr 1.311->0.008  render 2.452->3.380  cam 0.098->1.155  frus 0.011->0.010  cull 0.004->0.004  swap 0.100->0.100  cpu 3.591->3.328
```

## `render.avoidMatrixReads` (toggle `render.avoidMatrixReads`, throttling on in A and B, `gsr-r1` to `gsr-r11`)

```
gsr-r1 static threaded cores 1.93  frame 2.510->1.599 (-36.3%)  vs a2 -31.9%  glerr 0.004->0.004  render 2.304->1.238  cam 0.853->0.004  frus 0.014->0.002  cull 0.009->0.000  swap 0.069->0.251  cpu 2.372->1.314
gsr-r1 pan    threaded cores 1.76  frame 3.793->2.254 (-40.6%)  vs a2 -37.7%  glerr 0.009->0.007  render 3.480->1.850  cam 1.216->0.004  frus 0.016->0.001  cull 0.008->0.000  swap 0.088->0.232  cpu 3.445->1.995
gsr-r2 static single   cores 1.22  frame 2.821->2.600 (-7.8%)  vs a2 -14.2%  glerr 0.000->0.000  render 2.212->2.066  cam 0.012->0.007  frus 0.002->0.003  cull 0.001->0.001  swap 0.446->0.393  cpu 2.767->2.580
gsr-r2 pan    single   cores 1.16  frame 3.852->4.011 (+4.1%)  vs a2 -1.7%  glerr 0.001->0.000  render 3.241->3.347  cam 0.010->0.004  frus 0.002->0.001  cull 0.000->0.000  swap 0.432->0.472  cpu 3.820->3.964
gsr-r3 static threaded cores 1.90  frame 2.565->1.631 (-36.4%)  vs a2 -37.5%  glerr 0.005->0.004  render 2.347->1.283  cam 0.848->0.005  frus 0.014->0.002  cull 0.008->0.000  swap 0.079->0.238  cpu 2.440->1.427
gsr-r3 pan    threaded cores 1.78  frame 3.954->2.341 (-40.8%)  vs a2 -41.0%  glerr 0.011->0.008  render 3.614->1.928  cam 1.249->0.004  frus 0.016->0.001  cull 0.008->0.000  swap 0.096->0.238  cpu 3.534->2.109
gsr-r4 static single   cores 1.16  frame 3.091->2.932 (-5.2%)  vs a2 -1.4%  glerr 0.001->0.001  render 2.459->2.347  cam 0.014->0.008  frus 0.003->0.003  cull 0.001->0.000  swap 0.443->0.415  cpu 3.027->2.876
gsr-r4 pan    single   cores 1.11  frame 4.175->4.586 (+9.9%)  vs a2 +7.9%  glerr 0.001->0.001  render 3.522->3.838  cam 0.010->0.004  frus 0.002->0.001  cull 0.000->0.000  swap 0.446->0.515  cpu 4.108->4.447
gsr-r5 static threaded cores 1.86  frame 2.633->1.973 (-25.1%)  vs a2 -32.7%  glerr 0.004->0.005  render 2.405->1.460  cam 0.893->0.005  frus 0.015->0.002  cull 0.006->0.001  swap 0.086->0.357  cpu 2.440->1.624
gsr-r5 pan    threaded cores 1.80  frame 3.901->2.303 (-41.0%)  vs a2 -36.1%  glerr 0.010->0.008  render 3.569->1.920  cam 1.253->0.004  frus 0.017->0.001  cull 0.008->0.000  swap 0.087->0.208  cpu 3.571->2.029
gsr-r6 static threaded cores 1.87  frame 2.479->1.615 (-34.9%)  vs a2 -35.4%  glerr 0.004->0.003  render 2.277->1.319  cam 0.793->0.004  frus 0.013->0.002  cull 0.007->0.000  swap 0.067->0.186  cpu 2.343->1.352
gsr-r6 pan    mixed:TS cores 1.76  frame 3.835->2.197 (-42.7%)  vs a2 -43.2%  glerr 0.011->0.005  render 3.510->1.830  cam 1.212->0.003  frus 0.016->0.001  cull 0.007->0.000  swap 0.097->0.204  cpu 3.503->1.959
gsr-r7 static threaded cores 1.87  frame 2.612->1.591 (-39.1%)  vs a2 -45.3%  glerr 0.004->0.003  render 2.390->1.254  cam 0.856->0.005  frus 0.013->0.001  cull 0.006->0.000  swap 0.078->0.230  cpu 2.452->1.327
gsr-r7 pan    threaded cores 1.79  frame 3.947->2.331 (-40.9%)  vs a2 -40.8%  glerr 0.009->0.007  render 3.610->1.971  cam 1.253->0.004  frus 0.015->0.001  cull 0.008->0.000  swap 0.100->0.190  cpu 3.543->2.100
gsr-r8 static threaded cores 1.89  frame 2.598->1.644 (-36.7%)  vs a2 -40.3%  glerr 0.005->0.004  render 2.372->1.289  cam 0.868->0.004  frus 0.014->0.002  cull 0.008->0.000  swap 0.082->0.244  cpu 2.455->1.369
gsr-r8 pan    threaded cores 1.81  frame 3.653->2.023 (-44.6%)  vs a2 -41.3%  glerr 0.010->0.006  render 3.353->1.695  cam 1.179->0.003  frus 0.016->0.001  cull 0.007->0.000  swap 0.081->0.187  cpu 3.295->1.831
gsr-r9 static threaded cores 1.90  frame 2.587->1.712 (-33.8%)  vs a2 -33.7%  glerr 0.005->0.005  render 2.354->1.311  cam 0.835->0.005  frus 0.014->0.002  cull 0.007->0.000  swap 0.092->0.280  cpu 2.449->1.420
gsr-r9 pan    threaded cores 1.84  frame 3.748->2.248 (-40.0%)  vs a2 -34.8%  glerr 0.009->0.007  render 3.441->1.893  cam 1.221->0.004  frus 0.016->0.001  cull 0.007->0.000  swap 0.090->0.196  cpu 3.388->1.995
gsr-r10 static threaded cores 1.90  frame 2.519->1.624 (-35.5%)  vs a2 -38.7%  glerr 0.004->0.004  render 2.302->1.240  cam 0.904->0.005  frus 0.013->0.002  cull 0.007->0.000  swap 0.076->0.270  cpu 2.380->1.352
gsr-r10 pan    threaded cores 1.82  frame 3.729->2.315 (-37.9%)  vs a2 -35.3%  glerr 0.009->0.008  render 3.435->1.848  cam 1.285->0.004  frus 0.015->0.001  cull 0.007->0.000  swap 0.070->0.282  cpu 3.448->1.982
gsr-r11 static threaded cores 1.89  frame 2.548->1.896 (-25.6%)  vs a2 -20.4%  glerr 0.004->0.005  render 2.338->1.430  cam 0.875->0.006  frus 0.013->0.002  cull 0.007->0.001  swap 0.071->0.318  cpu 2.420->1.528
gsr-r11 pan    threaded cores 1.82  frame 3.739->2.299 (-38.5%)  vs a2 -34.9%  glerr 0.009->0.007  render 3.436->1.918  cam 1.191->0.004  frus 0.015->0.001  cull 0.007->0.000  swap 0.083->0.204  cpu 3.395->2.047
```

`gsr-r6` pan shows as mixed because its a2 segment ran at 1.58 cores; with `camera_setup` at
1.22 ms it is clearly in the threaded mode and is counted as threaded below (cores above 1.5 in
both A segments).

Medians over launches (vs a2; fps, p99 and 1% low: a2 against the B pool):

```
threaded static n=9 vs a2 median -35.4% range -45.3..-20.4 vs A -35.5%  ms 2.61->1.63  fps 383->613  p99 4.35->4.35  1%low 193->204  cpu 2.44->1.37  util 0.95->0.83  swap 0.08->0.25  alloc 59097->56354
threaded pan n=9 vs a2 median -37.7% range -43.2..-34.8 vs A -40.8%  ms 3.61->2.30  fps 277->435  p99 5.69->6.24  1%low 158->144  cpu 3.45->2.00  util 0.91->0.89  swap 0.09->0.20  alloc 67142->61359
single static n=2 vs a2 median -7.8% range -14.2..-1.4 vs A -6.5%  ms 3.00->2.77  fps 333->363  p99 4.90->4.09  1%low 181->220  cpu 2.90->2.73  util 0.98->0.99  swap 0.44->0.40  alloc 60199->59319
single pan n=2 vs a2 median +3.1% range -1.7..+7.9 vs A +7.0%  ms 4.17->4.30  fps 240->234  p99 6.29->7.01  1%low 127->125  cpu 3.96->4.21  util 0.99->0.98  swap 0.44->0.49  alloc 66624->66553
```

`gsr-verify1` is the verification run (`render.avoidMatrixReads=true`,
`render.avoidMatrixReads.verify=true`, full static/pan/fly benchmark); its frame times include
the real reads and are not a performance result.
