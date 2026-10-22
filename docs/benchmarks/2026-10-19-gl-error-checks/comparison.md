# render.throttleGlErrorChecks — in-process A/B, 200 mobs, 60 launches

Generated from the `runs/` summaries. A = option off (vanilla checks every frame), B = on.
`gle-r51` to `gle-r60` were run with NVIDIA's Threaded optimization forced on; see the last
section. In `gle-r1` to `gle-r50` (default driver settings), every launch landed in the single-threaded driver mode (process CPU 1.0-1.2 cores, swap
0.37-0.6 ms, except `gle-r47`: 0.01 ms swap at 1.1 cores). No launch landed in the threaded mode (about 1.9 cores, swap about 0.03 ms).

## Per launch (frame-weighted means of A = a1+a2 and B = b1+b2)

Segments with a mean swap above 1.5 ms are external stalls (another process holding the GPU)
and are excluded: `gle-r4` pan-a2 (3.9 ms swap) and `gle-r5` static-a1 (16.4 ms swap).

```
gle-r1 static single   cores 1.16  frame 2.688->2.732 (+1.6%)  p50 2.510->2.605 (+3.8%)  glerr 0.0062->0.0005  swap 0.403->0.399  render 2.124->2.174  cpu 2.621->2.667 
gle-r1 pan    single   cores 1.14  frame 3.713->4.172 (+12.4%)  p50 3.607->4.069 (+12.8%)  glerr 0.0029->0.0006  swap 0.385->0.465  render 3.143->3.497  cpu 3.662->4.014 
gle-r2 static single   cores 1.17  frame 3.255->2.997 (-7.9%)  p50 3.048->2.874 (-5.7%)  glerr 0.0078->0.0006  swap 0.455->0.422  render 2.586->2.394  cpu 3.127->2.930 
gle-r2 pan    single   cores 1.11  frame 3.900->3.851 (-1.3%)  p50 3.779->3.726 (-1.4%)  glerr 0.0025->0.0005  swap 0.408->0.408  render 3.304->3.261  cpu 3.797->3.790 
gle-r3 static single   cores 1.17  frame 3.140->2.997 (-4.6%)  p50 2.978->2.868 (-3.7%)  glerr 0.0077->0.0005  swap 0.492->0.448  render 2.448->2.367  cpu 3.026->2.912 
gle-r3 pan    single   cores 1.13  frame 3.943->3.943 (-0.0%)  p50 3.828->3.863 (+0.9%)  glerr 0.0027->0.0005  swap 0.417->0.422  render 3.334->3.322  cpu 3.881->3.889 
gle-r4 static single   cores 1.18  frame 3.066->2.942 (-4.1%)  p50 2.917->2.806 (-3.8%)  glerr 0.0074->0.0006  swap 0.432->0.417  render 2.437->2.352  cpu 2.965->2.863 
gle-r4 pan    single   cores 1.03  frame 3.909->4.140 (+5.9%)  p50 3.791->3.975 (+4.9%)  glerr 0.0023->0.0005  swap 0.401->0.450  render 3.320->3.476  cpu 3.884->4.066 excluded a2
gle-r5 static single   cores 0.94  frame 2.874->2.966 (+3.2%)  p50 2.772->2.831 (+2.1%)  glerr 0.0034->0.0005  swap 0.402->0.438  render 2.306->2.347  cpu 2.802->2.859 excluded a1
gle-r5 pan    single   cores 1.13  frame 3.895->3.868 (-0.7%)  p50 3.783->3.760 (-0.6%)  glerr 0.0024->0.0005  swap 0.400->0.401  render 3.305->3.282  cpu 3.821->3.803 
gle-r6 static single   cores 1.16  frame 2.913->2.819 (-3.2%)  p50 2.807->2.728 (-2.8%)  glerr 0.0068->0.0006  swap 0.402->0.391  render 2.334->2.266  cpu 2.898->2.788 
gle-r6 pan    single   cores 1.14  frame 3.918->3.928 (+0.3%)  p50 3.811->3.817 (+0.1%)  glerr 0.0025->0.0005  swap 0.402->0.408  render 3.329->3.335  cpu 3.864->3.850 
gle-r7 static single   cores 1.20  frame 2.937->3.016 (+2.7%)  p50 2.821->2.850 (+1.0%)  glerr 0.0071->0.0004  swap 0.471->0.453  render 2.279->2.379  cpu 2.788->2.931 
gle-r7 pan    single   cores 1.14  frame 3.680->3.837 (+4.3%)  p50 3.577->3.704 (+3.6%)  glerr 0.0024->0.0004  swap 0.402->0.428  render 3.104->3.225  cpu 3.629->3.756 
gle-r8 static single   cores 1.18  frame 2.558->2.491 (-2.6%)  p50 2.432->2.392 (-1.6%)  glerr 0.0054->0.0002  swap 0.380->0.367  render 2.025->1.985  cpu 2.521->2.471 
gle-r8 pan    single   cores 1.10  frame 3.560->3.535 (-0.7%)  p50 3.441->3.443 (+0.0%)  glerr 0.0026->0.0003  swap 0.384->0.381  render 3.008->2.988  cpu 3.525->3.479 
gle-r9 static single   cores 1.19  frame 2.562->2.477 (-3.3%)  p50 2.450->2.372 (-3.2%)  glerr 0.0059->0.0004  swap 0.371->0.360  render 2.038->1.982  cpu 2.530->2.448 
gle-r9 pan    single   cores 1.12  frame 3.583->3.604 (+0.6%)  p50 3.478->3.489 (+0.3%)  glerr 0.0026->0.0004  swap 0.388->0.387  render 3.023->3.045  cpu 3.545->3.566 
gle-r10 static single   cores 1.16  frame 2.885->2.851 (-1.2%)  p50 2.794->2.749 (-1.6%)  glerr 0.0069->0.0005  swap 0.393->0.392  render 2.312->2.293  cpu 2.847->2.797 
gle-r10 pan    single   cores 1.17  frame 4.043->3.934 (-2.7%)  p50 3.916->3.844 (-1.8%)  glerr 0.0029->0.0005  swap 0.402->0.395  render 3.440->3.346  cpu 3.979->3.880 
gle-r11 static single   cores 1.15  frame 2.939->2.818 (-4.1%)  p50 2.812->2.731 (-2.9%)  glerr 0.0072->0.0006  swap 0.402->0.383  render 2.351->2.270  cpu 2.851->2.768 
gle-r11 pan    single   cores 1.14  frame 3.958->3.944 (-0.3%)  p50 3.868->3.850 (-0.5%)  glerr 0.0031->0.0006  swap 0.397->0.398  render 3.360->3.350  cpu 3.903->3.845 
gle-r12 static single   cores 1.17  frame 2.846->2.812 (-1.2%)  p50 2.758->2.725 (-1.2%)  glerr 0.0069->0.0004  swap 0.393->0.392  render 2.272->2.255  cpu 2.797->2.772 
gle-r12 pan    single   cores 1.16  frame 4.272->3.992 (-6.5%)  p50 4.139->3.895 (-5.9%)  glerr 0.0031->0.0004  swap 0.445->0.411  render 3.610->3.386  cpu 4.132->3.913 
gle-r13 static single   cores 1.16  frame 2.931->3.080 (+5.1%)  p50 2.785->2.929 (+5.2%)  glerr 0.0068->0.0005  swap 0.483->0.490  render 2.269->2.408  cpu 2.820->2.959 
gle-r13 pan    single   cores 1.13  frame 4.063->3.979 (-2.1%)  p50 3.983->3.927 (-1.4%)  glerr 0.0029->0.0005  swap 0.473->0.455  render 3.389->3.328  cpu 3.897->3.871 
gle-r14 static single   cores 1.20  frame 3.076->3.103 (+0.9%)  p50 2.947->2.970 (+0.8%)  glerr 0.0072->0.0005  swap 0.432->0.445  render 2.447->2.469  cpu 3.008->3.000 
gle-r14 pan    single   cores 1.14  frame 4.297->4.193 (-2.4%)  p50 4.185->4.057 (-3.0%)  glerr 0.0031->0.0005  swap 0.439->0.432  render 3.637->3.547  cpu 4.219->4.091 
gle-r15 static single   cores 1.15  frame 3.129->2.949 (-5.7%)  p50 2.972->2.851 (-4.1%)  glerr 0.0074->0.0006  swap 0.474->0.419  render 2.459->2.356  cpu 3.016->2.865 
gle-r15 pan    single   cores 1.15  frame 4.417->4.197 (-5.0%)  p50 4.191->4.071 (-2.9%)  glerr 0.0034->0.0006  swap 0.454->0.427  render 3.737->3.560  cpu 4.282->4.113 
gle-r16 static single   cores 1.20  frame 3.099->2.951 (-4.8%)  p50 2.943->2.842 (-3.4%)  glerr 0.0071->0.0005  swap 0.429->0.413  render 2.469->2.360  cpu 3.025->2.881 
gle-r16 pan    single   cores 1.14  frame 4.156->4.136 (-0.5%)  p50 4.031->4.021 (-0.2%)  glerr 0.0030->0.0005  swap 0.424->0.428  render 3.521->3.494  cpu 4.082->4.074 
gle-r17 static single   cores 1.20  frame 3.052->2.994 (-1.9%)  p50 2.902->2.854 (-1.7%)  glerr 0.0074->0.0007  swap 0.426->0.419  render 2.430->2.394  cpu 2.998->2.928 
gle-r17 pan    single   cores 1.13  frame 4.172->4.178 (+0.1%)  p50 4.063->4.056 (-0.2%)  glerr 0.0032->0.0007  swap 0.422->0.429  render 3.541->3.541  cpu 4.084->4.077 
gle-r18 static single   cores 1.21  frame 3.030->3.064 (+1.1%)  p50 2.902->2.931 (+1.0%)  glerr 0.0075->0.0006  swap 0.426->0.441  render 2.410->2.435  cpu 2.963->3.020 
gle-r18 pan    single   cores 1.14  frame 4.334->4.272 (-1.4%)  p50 4.178->4.115 (-1.5%)  glerr 0.0031->0.0006  swap 0.454->0.453  render 3.655->3.597  cpu 4.185->4.178 
gle-r19 static single   cores 1.19  frame 2.956->3.106 (+5.0%)  p50 2.797->2.937 (+5.0%)  glerr 0.0074->0.0006  swap 0.443->0.457  render 2.321->2.454  cpu 2.841->3.031 
gle-r19 pan    single   cores 1.13  frame 4.239->4.038 (-4.7%)  p50 4.067->3.901 (-4.1%)  glerr 0.0030->0.0005  swap 0.481->0.462  render 3.537->3.371  cpu 4.114->3.933 
gle-r20 static single   cores 1.19  frame 2.980->2.944 (-1.2%)  p50 2.784->2.825 (+1.5%)  glerr 0.0072->0.0005  swap 0.428->0.437  render 2.368->2.335  cpu 2.904->2.879 
gle-r20 pan    single   cores 1.11  frame 3.989->4.322 (+8.3%)  p50 3.778->4.134 (+9.4%)  glerr 0.0027->0.0005  swap 0.429->0.469  render 3.363->3.631  cpu 3.859->4.202 
gle-r21 static single   cores 1.18  frame 3.112->2.867 (-7.9%)  p50 2.945->2.743 (-6.9%)  glerr 0.0076->0.0006  swap 0.448->0.434  render 2.465->2.265  cpu 3.028->2.808 
gle-r21 pan    single   cores 1.14  frame 4.004->4.108 (+2.6%)  p50 3.835->3.990 (+4.0%)  glerr 0.0024->0.0005  swap 0.435->0.441  render 3.369->3.464  cpu 3.936->3.988 
gle-r22 static single   cores 1.15  frame 3.105->2.799 (-9.9%)  p50 2.916->2.700 (-7.4%)  glerr 0.0074->0.0004  swap 0.455->0.407  render 2.447->2.229  cpu 3.022->2.641 
gle-r22 pan    single   cores 1.17  frame 3.976->3.834 (-3.6%)  p50 3.822->3.683 (-3.6%)  glerr 0.0028->0.0004  swap 0.427->0.420  render 3.346->3.225  cpu 3.892->3.785 
gle-r23 static single   cores 1.16  frame 2.970->2.892 (-2.6%)  p50 2.831->2.780 (-1.8%)  glerr 0.0072->0.0005  swap 0.442->0.428  render 2.338->2.292  cpu 2.866->2.806 
gle-r23 pan    single   cores 1.15  frame 3.880->3.810 (-1.8%)  p50 3.773->3.734 (-1.0%)  glerr 0.0026->0.0004  swap 0.419->0.413  render 3.272->3.208  cpu 3.802->3.753 
gle-r24 static single   cores 1.18  frame 3.428->3.211 (-6.3%)  p50 3.184->3.065 (-3.7%)  glerr 0.0080->0.0006  swap 0.521->0.493  render 2.675->2.516  cpu 3.250->3.090 
gle-r24 pan    single   cores 1.12  frame 4.400->4.720 (+7.3%)  p50 4.238->4.494 (+6.1%)  glerr 0.0029->0.0005  swap 0.462->0.501  render 3.713->3.970  cpu 4.271->4.502 
gle-r25 static single   cores 1.16  frame 3.797->3.431 (-9.6%)  p50 3.513->3.202 (-8.9%)  glerr 0.0099->0.0005  swap 0.530->0.484  render 2.996->2.724  cpu 3.624->3.242 
gle-r25 pan    single   cores 1.12  frame 5.154->5.384 (+4.5%)  p50 4.867->5.069 (+4.1%)  glerr 0.0033->0.0006  swap 0.517->0.548  render 4.354->4.531  cpu 4.970->5.124 
gle-r26 static single   cores 1.19  frame 3.576->3.541 (-1.0%)  p50 3.350->3.322 (-0.8%)  glerr 0.0094->0.0006  swap 0.487->0.484  render 2.845->2.818  cpu 3.357->3.418 
gle-r26 pan    single   cores 1.14  frame 4.902->4.876 (-0.5%)  p50 4.740->4.746 (+0.1%)  glerr 0.0034->0.0006  swap 0.478->0.481  render 4.157->4.135  cpu 4.696->4.682 
gle-r27 static single   cores 1.16  frame 3.557->3.408 (-4.2%)  p50 3.368->3.235 (-3.9%)  glerr 0.0090->0.0006  swap 0.492->0.473  render 2.831->2.720  cpu 3.445->3.246 
gle-r27 pan    single   cores 1.13  frame 5.089->4.968 (-2.4%)  p50 4.852->4.810 (-0.9%)  glerr 0.0034->0.0006  swap 0.496->0.562  render 4.320->4.149  cpu 4.945->4.739 
gle-r28 static single   cores 1.16  frame 3.465->3.524 (+1.7%)  p50 3.242->3.332 (+2.8%)  glerr 0.0085->0.0006  swap 0.482->0.503  render 2.752->2.794  cpu 3.286->3.396 
gle-r28 pan    single   cores 1.13  frame 4.781->4.782 (+0.0%)  p50 4.620->4.610 (-0.2%)  glerr 0.0032->0.0006  swap 0.475->0.470  render 4.055->4.062  cpu 4.626->4.582
```

## Per launch, `gle-r29` to `gle-r50` (16:40-17:20; muted from `gle-r32`)

Same method and format, plus `vs a2`: B against the second A segment only, which avoids the
first-segment penalty described below. No segment had a stall (mean swap above 1.5 ms).

```
gle-r29 static single   cores 1.23  frame 2.922->2.934 (+0.4%)  p50 2.817->2.819 (+0.1%)  glerr 0.0022->0.0007  swap 0.411->0.433  render 2.331->2.323  cpu 2.862->2.882  vs a2 +5.4%
gle-r29 pan    single   cores 1.14  frame 4.005->3.971 (-0.9%)  p50 3.901->3.820 (-2.1%)  glerr 0.0019->0.0008  swap 0.415->0.415  render 3.395->3.362  cpu 3.929->3.896  vs a2 -1.4%
gle-r30 static single   cores 1.19  frame 2.911->2.831 (-2.8%)  p50 2.809->2.738 (-2.5%)  glerr 0.0022->0.0006  swap 0.408->0.402  render 2.328->2.252  cpu 2.851->2.790  vs a2 -2.6%
gle-r30 pan    single   cores 1.15  frame 4.000->4.011 (+0.3%)  p50 3.891->3.935 (+1.1%)  glerr 0.0020->0.0006  swap 0.410->0.419  render 3.396->3.396  cpu 3.941->3.964  vs a2 +1.1%
gle-r31 static single   cores 1.20  frame 3.261->3.026 (-7.2%)  p50 3.080->2.901 (-5.8%)  glerr 0.0026->0.0009  swap 0.542->0.422  render 2.507->2.415  cpu 3.076->2.954  vs a2 -3.9%
gle-r31 pan    single   cores 1.15  frame 4.255->4.182 (-1.7%)  p50 4.140->4.056 (-2.0%)  glerr 0.0023->0.0008  swap 0.446->0.426  render 3.595->3.544  cpu 4.174->4.095  vs a2 +4.5%
gle-r32 static single   cores 1.22  frame 2.996->3.177 (+6.0%)  p50 2.866->3.024 (+5.5%)  glerr 0.0025->0.0007  swap 0.416->0.491  render 2.402->2.492  cpu 2.902->3.033  vs a2 +10.6%
gle-r32 pan    single   cores 1.10  frame 4.019->3.944 (-1.9%)  p50 3.927->3.869 (-1.5%)  glerr 0.0019->0.0006  swap 0.421->0.431  render 3.407->3.326  cpu 3.951->3.860  vs a2 +0.3%
gle-r33 static single   cores 1.17  frame 2.895->2.793 (-3.5%)  p50 2.788->2.702 (-3.1%)  glerr 0.0022->0.0006  swap 0.407->0.392  render 2.320->2.237  cpu 2.836->2.771  vs a2 -4.0%
gle-r33 pan    single   cores 1.13  frame 4.011->3.871 (-3.5%)  p50 3.890->3.793 (-2.5%)  glerr 0.0019->0.0006  swap 0.413->0.407  render 3.411->3.283  cpu 3.898->3.789  vs a2 +0.0%
gle-r34 static single   cores 1.19  frame 3.089->2.862 (-7.4%)  p50 2.970->2.774 (-6.6%)  glerr 0.0025->0.0007  swap 0.421->0.394  render 2.484->2.297  cpu 3.006->2.834  vs a2 -9.5%
gle-r34 pan    single   cores 1.10  frame 4.069->4.642 (+14.1%)  p50 4.031->4.474 (+11.0%)  glerr 0.0019->0.0008  swap 0.449->0.524  render 3.428->3.888  cpu 3.950->4.428  vs a2 +25.0%
gle-r35 static single   cores 1.19  frame 2.976->2.895 (-2.7%)  p50 2.886->2.821 (-2.2%)  glerr 0.0023->0.0007  swap 0.408->0.390  render 2.396->2.337  cpu 2.938->2.845  vs a2 -0.4%
gle-r35 pan    single   cores 1.11  frame 4.187->4.129 (-1.4%)  p50 4.049->4.031 (-0.4%)  glerr 0.0021->0.0007  swap 0.446->0.422  render 3.540->3.508  cpu 4.064->4.013  vs a2 -5.7%
gle-r36 static single   cores 1.13  frame 2.859->2.758 (-3.5%)  p50 2.765->2.650 (-4.2%)  glerr 0.0024->0.0007  swap 0.416->0.392  render 2.275->2.203  cpu 2.791->2.723  vs a2 +1.4%
gle-r36 pan    single   cores 1.10  frame 3.805->3.801 (-0.1%)  p50 3.706->3.725 (+0.5%)  glerr 0.0020->0.0007  swap 0.397->0.397  render 3.229->3.227  cpu 3.749->3.713  vs a2 +1.2%
gle-r37 static single   cores 1.14  frame 3.010->2.986 (-0.8%)  p50 2.896->2.852 (-1.5%)  glerr 0.0024->0.0007  swap 0.418->0.416  render 2.411->2.391  cpu 2.938->2.896  vs a2 +7.1%
gle-r37 pan    single   cores 1.12  frame 3.838->3.809 (-0.8%)  p50 3.717->3.687 (-0.8%)  glerr 0.0018->0.0005  swap 0.396->0.400  render 3.263->3.227  cpu 3.797->3.717  vs a2 +0.2%
gle-r38 static single   cores 1.18  frame 2.844->2.729 (-4.1%)  p50 2.732->2.638 (-3.4%)  glerr 0.0023->0.0006  swap 0.394->0.380  render 2.284->2.191  cpu 2.803->2.706  vs a2 -0.8%
gle-r38 pan    single   cores 1.14  frame 4.465->4.055 (-9.2%)  p50 4.251->3.948 (-7.1%)  glerr 0.0022->0.0007  swap 0.497->0.430  render 3.743->3.430  cpu 4.334->3.995  vs a2 -4.6%
gle-r39 static single   cores 1.13  frame 3.340->3.343 (+0.1%)  p50 3.181->3.138 (-1.4%)  glerr 0.0028->0.0008  swap 0.504->0.512  render 2.630->2.626  cpu 3.172->3.206  vs a2 +3.4%
gle-r39 pan    single   cores 1.10  frame 4.088->3.884 (-5.0%)  p50 3.972->3.780 (-4.8%)  glerr 0.0021->0.0005  swap 0.438->0.419  render 3.455->3.284  cpu 3.992->3.835  vs a2 -7.4%
gle-r40 static single   cores 1.13  frame 2.776->3.094 (+11.4%)  p50 2.642->2.964 (+12.2%)  glerr 0.0017->0.0007  swap 0.413->0.468  render 2.201->2.434  cpu 2.724->2.957  vs a2 +9.0%
gle-r40 pan    single   cores 1.11  frame 4.129->3.729 (-9.7%)  p50 4.015->3.559 (-11.4%)  glerr 0.0021->0.0006  swap 0.454->0.414  render 3.474->3.140  cpu 4.042->3.669  vs a2 -17.4%
gle-r41 static single   cores 1.12  frame 2.792->2.553 (-8.5%)  p50 2.669->2.444 (-8.4%)  glerr 0.0024->0.0007  swap 0.418->0.372  render 2.210->2.035  cpu 2.708->2.536  vs a2 -5.2%
gle-r41 pan    single   cores 1.11  frame 3.652->3.635 (-0.5%)  p50 3.547->3.564 (+0.5%)  glerr 0.0018->0.0008  swap 0.410->0.400  render 3.075->3.068  cpu 3.613->3.612  vs a2 -2.9%
gle-r42 static single   cores 1.15  frame 3.222->2.927 (-9.2%)  p50 3.085->2.827 (-8.4%)  glerr 0.0026->0.0007  swap 0.498->0.426  render 2.530->2.330  cpu 3.121->2.866  vs a2 -5.9%
gle-r42 pan    single   cores 1.08  frame 3.564->4.435 (+24.4%)  p50 3.485->4.167 (+19.6%)  glerr 0.0016->0.0007  swap 0.387->0.579  render 3.017->3.643  cpu 3.522->4.235  vs a2 +29.3%
gle-r43 static single   cores 1.13  frame 3.281->3.079 (-6.2%)  p50 3.083->2.946 (-4.4%)  glerr 0.0026->0.0007  swap 0.490->0.446  render 2.591->2.445  cpu 3.116->2.987  vs a2 +2.1%
gle-r43 pan    single   cores 1.11  frame 3.837->4.305 (+12.2%)  p50 3.712->4.145 (+11.7%)  glerr 0.0019->0.0007  swap 0.410->0.464  render 3.246->3.629  cpu 3.760->4.179  vs a2 +7.9%
gle-r44 static single   cores 1.14  frame 3.323->3.035 (-8.7%)  p50 3.120->2.894 (-7.2%)  glerr 0.0024->0.0009  swap 0.500->0.436  render 2.620->2.414  cpu 3.146->2.982  vs a2 +0.9%
gle-r44 pan    single   cores 1.12  frame 3.757->3.664 (-2.5%)  p50 3.666->3.595 (-1.9%)  glerr 0.0016->0.0005  swap 0.396->0.395  render 3.185->3.102  cpu 3.674->3.602  vs a2 -7.4%
gle-r45 static single   cores 1.18  frame 2.761->2.612 (-5.4%)  p50 2.626->2.488 (-5.3%)  glerr 0.0019->0.0005  swap 0.399->0.390  render 2.204->2.070  cpu 2.722->2.554  vs a2 -10.5%
gle-r45 pan    single   cores 1.12  frame 3.465->3.364 (-2.9%)  p50 3.320->3.284 (-1.1%)  glerr 0.0015->0.0005  swap 0.378->0.367  render 2.932->2.851  cpu 3.385->3.301  vs a2 -7.6%
gle-r46 static single   cores 1.15  frame 3.284->4.428 (+34.8%)  p50 3.110->4.066 (+30.7%)  glerr 0.0026->0.0011  swap 0.493->0.934  render 2.585->3.204  cpu 3.119->3.866  vs a2 +35.5%
gle-r46 pan    single   cores 1.09  frame 4.665->4.462 (-4.4%)  p50 4.509->4.295 (-4.8%)  glerr 0.0022->0.0005  swap 0.498->0.461  render 3.929->3.779  cpu 4.493->4.219  vs a2 -8.7%
gle-r47 static single   cores 1.10  frame 3.210->3.009 (-6.3%)  p50 2.905->2.766 (-4.8%)  glerr 0.0020->0.0007  swap 0.011->0.009  render 3.018->2.824  cpu 2.969->2.824  vs a2 -10.5%
gle-r47 pan    single   cores 1.09  frame 4.689->4.219 (-10.0%)  p50 4.393->4.012 (-8.7%)  glerr 0.0015->0.0004  swap 0.009->0.009  render 4.483->4.031  cpu 4.469->4.051  vs a2 -2.5%
gle-r48 static single   cores 1.14  frame 3.018->2.976 (-1.4%)  p50 2.842->2.828 (-0.5%)  glerr 0.0032->0.0006  swap 0.518->0.441  render 2.323->2.358  cpu 2.862->2.891  vs a2 +7.7%
gle-r48 pan    single   cores 1.10  frame 3.949->3.888 (-1.5%)  p50 3.794->3.783 (-0.3%)  glerr 0.0028->0.0004  swap 0.408->0.402  render 3.352->3.303  cpu 3.883->3.814  vs a2 -0.6%
gle-r49 static single   cores 1.12  frame 2.821->2.935 (+4.0%)  p50 2.687->2.803 (+4.3%)  glerr 0.0022->0.0007  swap 0.416->0.431  render 2.244->2.324  cpu 2.772->2.838  vs a2 +8.0%
gle-r49 pan    single   cores 1.12  frame 4.284->4.087 (-4.6%)  p50 4.130->3.939 (-4.6%)  glerr 0.0023->0.0007  swap 0.474->0.459  render 3.600->3.434  cpu 4.185->3.997  vs a2 -6.9%
gle-r50 static single   cores 1.20  frame 3.016->2.988 (-0.9%)  p50 2.908->2.878 (-1.1%)  glerr 0.0026->0.0008  swap 0.437->0.425  render 2.398->2.386  cpu 2.954->2.936  vs a2 +2.1%
gle-r50 pan    single   cores 1.14  frame 4.371->4.315 (-1.3%)  p50 4.207->4.098 (-2.6%)  glerr 0.0021->0.0006  swap 0.454->0.478  render 3.697->3.629  cpu 4.246->4.215  vs a2 -0.4%
```

Over all 50 launches: median per-launch change static −2.7% (36 of 50 faster with B), pan
−1.1% (35 of 50); against a2 alone, median +0.5% (static) and +0.6% (pan).

## Segment position effect

Median frame time of each segment relative to its launch's mean (28 launches, stalls excluded):
static a1 1.035, b1 0.997, b2 0.981, a2 0.991; pan a1 1.024, b1 0.999, b2 0.987, a2 0.981.
The first segment of each phase is about 3% slower regardless of the option, so A carries a
small penalty. Against a2 alone, B is 0.2% faster in static and 1.3% slower in pan: no
consistent direction.

## Pooled, `gle-r1` to `gle-r28` (tools/compare_benchmarks.py --toggle, all segments including the two stalls)

The two stalled segments are both in A, which inflates A's 1% low, p99 and swap figures below.

A (option off) vs B (option on) within 28 run(s)

### static

| metric | A | B | change |
|---|---:|---:|---:|
| frames | 91063 | 94276 | +3.5% |
| avg fps | 314.2 | 336.6 | +7.1% |
| 1% low fps | 48.9 | 170.4 | +248.5% |
| frame p50 ms | 2.883 | 2.838 | -1.6% |
| frame p95 ms | 4.307 | 4.100 | -4.8% |
| frame p99 ms | 5.444 | 4.967 | -8.8% |
| frame p99.9 ms | 8.519 | 6.446 | -24.3% |
| client thread CPU ms/frame | 2.953 | 2.889 | -2.2% |
| process CPU cores | 1.180 | 1.160 | -1.7% |
| render mean ms | 2.412 | 2.363 | -2.0% |
| terrain_setup mean ms | 0.061 | 0.059 | -3.4% |
| visibility mean ms | 0.001 | 0.001 | -25.8% |
| chunk_upload mean ms | 0.001 | 0.001 | -25.1% |
| entities mean ms | 1.064 | 1.054 | -0.9% |
| entity_loop mean ms | 0.996 | 0.989 | -0.7% |
| block_entity_loop mean ms | 0.065 | 0.062 | -4.3% |
| swap mean ms | 0.577 | 0.429 | -25.6% |
| gl_error_check mean ms | 0.007 | 0.001 | -92.7% |
| alloc KB/frame | 58.8 | 58.3 | -0.8% |
| alloc MB/s | 18.0 | 19.2 | +6.3% |
| visible_chunks | 1374.0 | 1374.0 | +0.0% |
| pending_chunks | 0.002 | 0.002 | -12.4% |
| visibility_searches | 0.001 | 0.001 | -15.7% |

### pan

| metric | A | B | change |
|---|---:|---:|---:|
| frames | 101203 | 101859 | +0.6% |
| avg fps | 240.9 | 242.5 | +0.7% |
| 1% low fps | 82.7 | 116.7 | +41.2% |
| frame p50 ms | 3.943 | 3.949 | +0.2% |
| frame p95 ms | 5.738 | 5.751 | +0.2% |
| frame p99 ms | 7.063 | 7.065 | +0.0% |
| frame p99.9 ms | 10.8 | 9.904 | -8.3% |
| client thread CPU ms/frame | 4.022 | 4.022 | +0.0% |
| process CPU cores | 1.144 | 1.124 | -1.8% |
| render mean ms | 3.480 | 3.480 | +0.0% |
| terrain_setup mean ms | 0.664 | 0.671 | +1.0% |
| visibility mean ms | 0.601 | 0.612 | +1.7% |
| chunk_upload mean ms | 0.022 | 0.001 | -93.3% |
| entities mean ms | 1.154 | 1.164 | +0.9% |
| entity_loop mean ms | 1.099 | 1.110 | +1.0% |
| block_entity_loop mean ms | 0.052 | 0.051 | -1.3% |
| swap mean ms | 0.464 | 0.437 | -5.8% |
| gl_error_check mean ms | 0.003 | 0.001 | -82.0% |
| alloc KB/frame | 65.1 | 64.5 | -0.9% |
| alloc MB/s | 15.3 | 15.3 | -0.2% |
| visible_chunks | 1467.3 | 1489.5 | +1.5% |
| pending_chunks | 7.139 | 0.009 | -99.9% |
| visibility_searches | 0.999 | 0.999 | +0.0% |

## Threaded optimization forced on, `gle-r51` to `gle-r60` (17:24-17:43)

NVIDIA Control Panel, global profile, Threaded optimization = On. 7 of 10 launches ran in the
threaded mode; `gle-r52`, `r53` and `r55` stayed single-threaded. `vs a2` = B against the second
A segment only. No segment had a stall.

```
gle-r51 static threaded cores 1.79  frame 2.601->2.576 (-0.9%)  p50 2.489->2.415 (-3.0%)  glerr 0.8390->0.0046  swap 0.067->0.084  render 1.597->2.351  cpu 2.421->2.427  vs a2 +0.8%
gle-r51 pan    threaded cores 1.63  frame 4.012->3.407 (-15.1%)  p50 3.879->3.286 (-15.3%)  glerr 1.3234->0.0086  swap 0.094->0.069  render 2.460->3.153  cpu 3.514->3.076  vs a2 -16.2%
gle-r52 static single   cores 1.16  frame 2.768->2.752 (-0.6%)  p50 2.671->2.657 (-0.5%)  glerr 0.0024->0.0007  swap 0.417->0.416  render 2.191->2.174  cpu 2.741->2.739  vs a2 +4.5%
gle-r52 pan    single   cores 1.12  frame 3.812->4.052 (+6.3%)  p50 3.686->3.932 (+6.7%)  glerr 0.0019->0.0007  swap 0.430->0.481  render 3.200->3.373  cpu 3.791->4.009  vs a2 +0.6%
gle-r53 static single   cores 1.14  frame 3.223->3.013 (-6.5%)  p50 3.066->2.906 (-5.2%)  glerr 0.0026->0.0010  swap 0.475->0.435  render 2.550->2.397  cpu 3.132->2.965  vs a2 -1.9%
gle-r53 pan    single   cores 1.10  frame 4.408->4.136 (-6.2%)  p50 4.240->3.987 (-6.0%)  glerr 0.0022->0.0006  swap 0.475->0.449  render 3.717->3.490  cpu 4.278->4.053  vs a2 -0.2%
gle-r54 static threaded cores 1.85  frame 2.655->2.417 (-9.0%)  p50 2.526->2.317 (-8.2%)  glerr 0.8855->0.0046  swap 0.076->0.060  render 1.593->2.228  cpu 2.480->2.273  vs a2 -9.6%
gle-r54 pan    threaded cores 1.65  frame 3.859->3.705 (-4.0%)  p50 3.747->3.598 (-4.0%)  glerr 1.3120->0.0100  swap 0.080->0.075  render 2.340->3.419  cpu 3.397->3.329  vs a2 +1.1%
gle-r55 static single   cores 1.17  frame 3.093->2.989 (-3.4%)  p50 2.908->2.822 (-3.0%)  glerr 0.0026->0.0007  swap 0.481->0.515  render 2.421->2.295  cpu 3.010->2.890  vs a2 -10.7%
gle-r55 pan    single   cores 1.11  frame 3.550->3.813 (+7.4%)  p50 3.461->3.719 (+7.5%)  glerr 0.0017->0.0006  swap 0.402->0.469  render 2.985->3.163  cpu 3.524->3.744  vs a2 +5.6%
gle-r56 static threaded cores 1.85  frame 2.217->2.190 (-1.2%)  p50 2.142->2.123 (-0.9%)  glerr 0.7966->0.0035  swap 0.048->0.050  render 1.301->2.034  cpu 2.105->2.083  vs a2 +0.7%
gle-r56 pan    threaded cores 1.69  frame 3.122->3.059 (-2.0%)  p50 3.035->2.969 (-2.2%)  glerr 1.1088->0.0064  swap 0.054->0.057  render 1.873->2.846  cpu 2.852->2.782  vs a2 -1.5%
gle-r57 static threaded cores 1.82  frame 2.194->2.123 (-3.2%)  p50 2.128->2.050 (-3.7%)  glerr 0.8060->0.0034  swap 0.048->0.050  render 1.272->1.974  cpu 2.084->1.977  vs a2 -3.2%
gle-r57 pan    threaded cores 1.67  frame 3.097->3.078 (-0.6%)  p50 2.983->2.995 (+0.4%)  glerr 1.1121->0.0071  swap 0.056->0.058  render 1.845->2.862  cpu 2.741->2.817  vs a2 -0.2%
gle-r58 static threaded cores 1.78  frame 2.284->2.188 (-4.2%)  p50 2.207->2.109 (-4.5%)  glerr 0.8463->0.0036  swap 0.050->0.053  render 1.312->2.027  cpu 2.158->2.041  vs a2 -3.7%
gle-r58 pan    threaded cores 1.68  frame 3.216->3.105 (-3.5%)  p50 3.127->3.020 (-3.4%)  glerr 1.1797->0.0073  swap 0.056->0.057  render 1.890->2.886  cpu 2.954->2.859  vs a2 -2.3%
gle-r59 static threaded cores 1.86  frame 2.267->2.124 (-6.3%)  p50 2.190->2.050 (-6.4%)  glerr 0.8039->0.0035  swap 0.051->0.050  render 1.340->1.973  cpu 2.118->2.021  vs a2 -2.8%
gle-r59 pan    threaded cores 1.66  frame 3.307->3.460 (+4.6%)  p50 3.210->3.336 (+3.9%)  glerr 1.1300->0.0086  swap 0.060->0.075  render 2.021->3.192  cpu 3.037->3.157  vs a2 -1.6%
gle-r60 static threaded cores 1.86  frame 2.443->2.421 (-0.9%)  p50 2.349->2.317 (-1.4%)  glerr 0.8231->0.0045  swap 0.059->0.073  render 1.473->2.219  cpu 2.286->2.239  vs a2 +0.8%
gle-r60 pan    threaded cores 1.65  frame 3.584->3.420 (-4.6%)  p50 3.480->3.343 (-3.9%)  glerr 1.2034->0.0085  swap 0.069->0.067  render 2.196->3.164  cpu 3.236->3.120  vs a2 -4.1%
```

Threaded launches only: B vs A median −3.2% (static, 7 of 7 faster) and −3.5% (pan, 6 of 7);
vs a2 median −2.8% (static, 4 of 7) and −1.6% (pan, 6 of 7), about −0.06 ms. Of the
`gl_error_check` time removed, 72-104% (median 87% static, 85% pan) reappears in `render`.
