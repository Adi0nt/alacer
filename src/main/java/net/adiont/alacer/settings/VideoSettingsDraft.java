package net.adiont.alacer.settings;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/** Edits are detached from the live renderer until an explicit successful Apply. */
public final class VideoSettingsDraft {
	private final Map<String,Integer> original = new LinkedHashMap<>(), values = new LinkedHashMap<>();
	public VideoSettingsDraft(Map<String,Integer> snapshot) {
		for (VideoSetting s : VideoSettingsCatalog.ALL) {
			int value = s.normalize(snapshot.getOrDefault(s.key(), s.defaultValue()));
			this.original.put(s.key(),value); this.values.put(s.key(),value);
		}
	}
	public int value(VideoSetting setting) { return this.values.get(setting.key()); }
	public void set(VideoSetting setting, int value) { this.values.put(setting.key(),setting.normalize(value)); }
	public void cycle(VideoSetting setting, int direction) {
		int next = value(setting) + setting.step()*direction;
		if (!setting.slider()) { next = Math.floorMod(next,setting.maximum()+1); }
		set(setting,next);
	}
	public boolean changed(VideoSetting setting) { return !this.values.get(setting.key()).equals(this.original.get(setting.key())); }
	public Map<String,Integer> changes() {
		Map<String,Integer> result = new LinkedHashMap<>();
		for (VideoSetting s : VideoSettingsCatalog.ALL) { if (changed(s)) { result.put(s.key(),value(s)); } }
		return result;
	}
	public void accept() { this.original.putAll(this.values); }
	public void reset(VideoSetting.Category category, Predicate<VideoSetting> editable) {
		for (VideoSetting s : VideoSettingsCatalog.ALL) { if (s.category()==category && editable.test(s)) { set(s,s.defaultValue()); } }
	}
	public List<VideoSetting> matching(VideoSetting.Category category, String query) {
		String q = query.strip().toLowerCase(Locale.ROOT);
		return VideoSettingsCatalog.ALL.stream().filter(s -> (q.isEmpty() ? s.category()==category : true)
			&& (s.title()+" "+s.description()+" "+s.category().title).toLowerCase(Locale.ROOT).contains(q)).toList();
	}
}
