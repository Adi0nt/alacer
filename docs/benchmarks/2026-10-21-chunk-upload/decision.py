"""Post-measurement exactness audit and application of the frozen session 27 rule; no reclassification of CSVs."""
import json
from pathlib import Path
import statistics as st

HERE = Path(__file__).resolve().parent


def main():
    m = json.loads((HERE/'metrics.json').read_bytes())
    scenes = {}
    for scene in ('near','high'):
        controls = [r for r in m['runs'] if r['scene']==scene and not r['detail']]
        diagnostics = [r for r in m['runs'] if r['scene']==scene and r['detail']]
        checks = []
        for r in controls:
            p = r['phases']['fresh-flight']
            a, b = p['groups']['all'], p['groups']['slow99']
            checks.append({'label':r['label'], 'mean_upload_fraction':a['upload_fraction'],
                           'slow99_upload_fraction':b['upload_fraction'], 'slow99_upload_ms':b['sections_ms']['chunk_upload'],
                           'mean_test':a['upload_fraction']>=.10,
                           'tail_test':b['upload_fraction']>=.20 and b['sections_ms']['chunk_upload']>=.5})
        count = sum(c['mean_test'] or c['tail_test'] for c in checks)
        assert m['scenes'][scene]['large_region_rule'] == (count>=2)
        comparisons = {}
        for k in ('frame_mean_ms','frame_p99_ms','tick_frame_p99_ms','cpu_ms_per_frame','allocation_bytes_per_frame'):
            a = st.median(r['phases']['fresh-flight'][k] for r in controls)
            b = st.median(r['phases']['fresh-flight'][k] for r in diagnostics)
            comparisons[k] = {'control_median':a,'diagnostic_median':b,'relative_change':b/a-1}
        a = st.median(r['phases']['fresh-flight']['groups']['slow99']['sections_ms']['frame'] for r in controls)
        b = st.median(r['phases']['fresh-flight']['groups']['slow99']['sections_ms']['frame'] for r in diagnostics)
        comparisons['slow99_frame_mean_ms'] = {'control_median':a,'diagnostic_median':b,'relative_change':b/a-1}
        scenes[scene] = {'checks':checks, 'qualifying_controls':count,'required_controls':2,
                         'large_region_rule':count>=2, 'corroboration':m['scenes'][scene]['list_compilation_corroborated'],
                         'control_diagnostic_observations_not_causal_overhead_estimates':comparisons}
    exactness = [
        {'idea':'Remove or batch required GL list compilation', 'exact_removable_set_established':False,
         'reason':'94-95% is the uploadGlList scope, not redundant calls. Vanilla binds each compiled list to one render chunk/layer, records its transform and vertex state, and publishes after futures complete. No duplicate-content/epoch census or proven equivalent representation removes a measured subset. Grouping changes object ownership and ordering and needs a new design/series.'},
        {'idea':'Skip upload of empty geometry', 'exact_removable_set_established':False,
         'reason':'ChunkRenderWorker tests CompiledChunk.hasLayer(layer) before rebuild uploads, so never-started layers are already skipped. A started layer may still have zero emitted vertices; this series has no per-list vertex/byte census to quantify those uploads. More worker builds than GL list calls does not identify a removable share.'},
        {'idea':'Deduplicate, defer, reprioritize or change drain budget', 'exact_removable_set_established':False,
         'reason':'No exact content/invalidation identity or visible-update latency was recorded. Dirty-set ages are lower bounds and queued entries omit active work; schedules/starts/uploads count different units. Deferring required work can lower frame cost without delivering the same throughput/latency. FIFO/cancellation/publication cannot be changed on these counters.'},
        {'idea':'Force VBO instead of display lists', 'exact_removable_set_established':False,
         'reason':'Explicitly excluded by the frozen rule and session 14 boundary; no VBO comparison ran in this series.'},
    ]
    assert not all(v['large_region_rule'] for v in scenes.values())
    decision = {'session':27,'named_candidate':None,'implemented_optimization':False,'promoted':False,
                'scenes':scenes,'exactness_audit_after_measurement':exactness,
                'reason':'Neither scene reaches the frozen 2/3 control region-size gate (near 1/3, high 0/3); subregion corroboration passes. No exact removable-work set is established independently either.',
                'later_correctness_lifecycle_ordering_throughput_latency_tail_cpu_allocation_gates':'NOT RUN / NOT MET: this is attribution, no candidate A/B',
                'gpu_boundary':'4859/4859 timestamp pairs completed over all diagnostic phases; no dropped/outstanding pairs. Command-stream elapsed is not GPU busy time or driver CPU time.',
                'source_snapshot':m['source_snapshot']}
    (HERE/'decision.json').write_bytes((json.dumps(decision,indent=2)+'\n').encode())
    print(decision['reason'])


if __name__ == '__main__':
    main()
