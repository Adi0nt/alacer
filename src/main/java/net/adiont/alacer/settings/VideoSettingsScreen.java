package net.adiont.alacer.settings;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import net.adiont.alacer.Alacer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.Window;

/** Staged video controls built from Minecraft's own menu widgets and textures. */
public final class VideoSettingsScreen extends Screen {
	private static final int TEXT = 0xffffff, MUTED = 0xaaaaaa, PRESET_BUTTONS = 20;
	private static final int ROW_HEIGHT = 24, PRESETS_WIDTH = 64, TAB_ROW_WIDTH = 410;
	private final Screen parent;
	private VideoSettingsBackend backend;
	private VideoSettingsDraft draft;
	private VideoSetting.Category category = VideoSetting.Category.GRAPHICS;
	private TextFieldWidget search;
	private String query = "", status = "", modal;
	private int offset, left, contentWidth, top, bottom, pageSize, focusId = -1;
	private boolean keyboardFocus, scrolling;
	private long hoverSince;
	private String hoverKey;
	private List<VideoSetting> matches = List.of();
	private final List<SettingButton> rows = new ArrayList<>();
	private SettingButton dragging;
	public VideoSettingsScreen(Screen parent) { this.parent = parent; }

	@Override public void init() {
		if (this.backend == null) {
			this.backend = new VideoSettingsBackend(this.minecraft);
			this.draft = new VideoSettingsDraft(this.backend.snapshot());
		}
		this.contentWidth = Math.min(310,this.width-20);
		this.left = (this.width-this.contentWidth)/2;
		this.top = 92;
		this.bottom = this.height-60;
		this.pageSize = Math.max(1,(this.bottom-this.top)/ROW_HEIGHT)*2;
		this.search = new TextFieldWidget(90,this.textRenderer,this.left+PRESETS_WIDTH+4,this.top-26,this.contentWidth-28-PRESETS_WIDTH,20);
		this.search.setMaxLength(80);
		this.search.setHasBorder(true);
		this.search.setText(this.query);
		Keyboard.enableRepeatEvents(true);
		rebuild();
	}
	private void rebuild() {
		this.buttons.clear(); this.rows.clear();
		// The tab row may be wider than the settings columns so every category name fits at common GUI scales.
		int tabs = VideoSetting.Category.values().length, tabWidth = Math.min(TAB_ROW_WIDTH,this.width-20), tabLeft = (this.width-tabWidth)/2;
		for (VideoSetting.Category c : VideoSetting.Category.values()) {
			int x = tabLeft+c.ordinal()*(tabWidth+2)/tabs;
			int w = (tabWidth+2)/tabs-2;
			this.buttons.add(new MenuButton(10+c.ordinal(),x,37,w,20,c.title,this.category==c && this.query.isEmpty()));
		}
		this.matches = this.draft.matching(this.category,this.query);
		this.offset = Math.max(0,Math.min(this.offset,maxOffset()));
		this.offset -= this.offset%2;
		for (int i = 0; i < this.pageSize && i+this.offset < this.matches.size(); i++) {
			VideoSetting setting = this.matches.get(i+this.offset);
			int buttonWidth=(this.contentWidth-10)/2;
			SettingButton row = new SettingButton(1000+i,setting,this.left+i%2*(buttonWidth+10),this.top+i/2*ROW_HEIGHT,buttonWidth);
			row.active = this.backend.lockedReason(setting)==null;
			this.rows.add(row); this.buttons.add(row);
		}
		int y = this.height-27;
		int buttonWidth=(this.contentWidth-18)/4;
		this.buttons.add(new MenuButton(0,this.left+3*(buttonWidth+6),y,buttonWidth,20,"Done",false));
		MenuButton apply = new MenuButton(1,this.left+2*(buttonWidth+6),y,buttonWidth,20,"Apply",false);
		apply.active = !this.draft.changes().isEmpty(); this.buttons.add(apply);
		this.buttons.add(new MenuButton(2,this.left+buttonWidth+6,y,buttonWidth,20,"Cancel",false));
		MenuButton reset = new MenuButton(3,this.left,y,buttonWidth,20,"Reset page",false);
		reset.active = this.query.isEmpty() && VideoSettingsCatalog.ALL.stream().anyMatch(s -> s.category()==this.category && this.backend.lockedReason(s)==null);
		this.buttons.add(reset);
		this.buttons.add(new MenuButton(4,this.left+this.contentWidth-20,this.top-26,20,20,"x",false));
		MenuButton presets = new MenuButton(5,this.left,this.top-26,PRESETS_WIDTH,20,"Presets...",false);
		presets.active = VideoSettingsPresets.KEYS.stream().anyMatch(k -> this.backend.lockedReason(VideoSettingsCatalog.byKey(k))==null);
		this.buttons.add(presets);
		if (this.modal != null) {
			for (ButtonWidget b : this.buttons) { b.active = false; }
			int cy = this.height/2+29;
			if (this.modal.equals("preset")) {
				VideoSettingsPresets[] choices = VideoSettingsPresets.values();
				for (int i = 0; i < choices.length; i++) {
					this.buttons.add(new MenuButton(PRESET_BUTTONS+i,this.width/2-117+i%2*124,cy+i/2*24,110,20,choices[i].title,false));
				}
				this.buttons.add(new MenuButton(7,this.width/2+7,cy+24,110,20,"Keep editing",false));
			} else {
				this.buttons.add(new MenuButton(6,this.width/2-117,cy,110,20,this.modal.equals("reset")?"Reset page":"Discard",false));
				this.buttons.add(new MenuButton(7,this.width/2+7,cy,110,20,"Keep editing",false));
			}
		}
	}
	@Override protected void buttonClicked(ButtonWidget button) {
		if (!button.active) { return; }
		this.focusId = button.id;
		if (button instanceof SettingButton row) {
			if (row.setting.slider()) {
				int mouseX=Mouse.getX()*this.width/this.minecraft.width;
				setSlider(row,mouseX); this.dragging=row;
			}
			else { this.draft.cycle(row.setting,1); }
			this.status=""; refreshApply(); return;
		}
		switch (button.id) {
			case 0 -> { if (apply()) { this.minecraft.openScreen(this.parent); } }
			case 1 -> apply();
			case 2 -> leave();
			case 3 -> { this.modal="reset"; rebuild(); }
			case 4 -> { this.query=""; this.search.setText(""); this.offset=0; rebuild(); this.search.setFocused(true); }
			case 5 -> { this.modal="preset"; rebuild(); }
			case 6 -> {
				if ("reset".equals(this.modal)) {
					this.draft.reset(this.category,s -> this.backend.lockedReason(s)==null);
					this.modal=null; this.status="\u00a7ePage defaults staged. Apply to save."; rebuild();
				} else { this.minecraft.openScreen(this.parent); }
			}
			case 7 -> { this.modal=null; rebuild(); }
			default -> {
				if (presetButton(button.id)) {
					VideoSettingsPresets preset=VideoSettingsPresets.values()[button.id-PRESET_BUTTONS];
					this.draft.applyPreset(preset,s -> this.backend.lockedReason(s)==null);
					this.modal=null; this.status=this.draft.changes().isEmpty()?"\u00a77"+preset.title+" preset already matches.":"\u00a7e"+preset.title+" preset staged. Apply to save.";
					rebuild(); return;
				}
				if (button.id>=10 && button.id<10+VideoSetting.Category.values().length) {
					this.category=VideoSetting.Category.values()[button.id-10]; this.query="";
					this.search.setText(""); this.offset=0; this.focusId=button.id; rebuild();
				}
			}
		}
	}
	private void refreshApply() {
		if (!this.draft.changes().isEmpty()) { this.status=""; }
		for (ButtonWidget b : this.buttons) { if (b.id==1) { b.active=!this.draft.changes().isEmpty(); } }
	}
	private boolean apply() {
		try {
			this.backend.apply(this.draft.changes());
			this.draft.accept(); this.status=this.backend.restartRequired()?"\u00a76Saved. Restart Minecraft to finish applying changes.":"\u00a7aSettings applied and saved.";
			Window window = new Window(this.minecraft);
			this.init(this.minecraft,window.getWidth(),window.getHeight());
			return true;
		} catch (IOException | RuntimeException e) {
			Alacer.LOGGER.error("Could not apply video settings",e);
			this.status="\u00a7c"+(e instanceof IOException?"Could not save: "+e.getMessage()+". Edits kept.":"Some settings could not be applied. Edits kept; see the game log.");
			return false;
		}
	}
	private void leave() {
		if (!this.draft.changes().isEmpty()) { this.modal="discard"; rebuild(); }
		else { this.minecraft.openScreen(this.parent); }
	}
	private void setSlider(SettingButton row, int mouseX) {
		float fraction=Math.max(0,Math.min(1,(mouseX-row.x-4)/(float)(row.getWidth()-8)));
		this.draft.set(row.setting,Math.round(row.setting.minimum()+fraction*(row.setting.maximum()-row.setting.minimum())));
		refreshApply();
	}
	@Override protected void mouseClicked(int x, int y, int button) {
		this.keyboardFocus=false; this.focusId=-1; this.hoverKey=null;
		if (this.modal==null && button==0 && this.matches.size()>this.pageSize && x>=this.left+this.contentWidth+4 && x<=this.left+this.contentWidth+10 && y>=this.top && y<this.top+this.pageSize/2*ROW_HEIGHT) {
			this.scrolling=true; scrollAt(y); return;
		}
		if (this.modal==null) {
			this.search.mouseClicked(x,y,button);
			if (button==1) {
				for (SettingButton row : this.rows) {
					if (row.active && row.contains(x,y)) { this.draft.cycle(row.setting,-1); refreshApply(); return; }
				}
			}
		}
		super.mouseClicked(x,y,button);
	}
	@Override protected void mouseDragged(int x, int y, int button, long elapsed) {
		if (this.scrolling) { scrollAt(y); }
		else if (this.dragging!=null && this.dragging.active) { setSlider(this.dragging,x); }
	}
	@Override protected void mouseReleased(int x, int y, int button) { this.dragging=null; this.scrolling=false; super.mouseReleased(x,y,button); }
	private void scrollAt(int y) {
		int track=this.pageSize/2*ROW_HEIGHT-4;
		int thumb=Math.max(12,track*this.pageSize/Math.max(1,this.matches.size()));
		this.offset=2*Math.round((float)(y-this.top-thumb/2)/Math.max(1,track-thumb)*maxOffset()/2);
		rebuild();
	}
	private int maxOffset() { return Math.max(0,((this.matches.size()+1)/2-this.pageSize/2)*2); }
	@Override public void handleMouse() {
		super.handleMouse();
		int wheel=Mouse.getEventDWheel();
		if (this.modal==null && wheel!=0) { this.offset+=wheel<0?2:-2; this.focusId=-1; rebuild(); }
	}
	@Override public void tick() { this.search.tick(); }
	@Override public void removed() { Keyboard.enableRepeatEvents(false); }
	@Override protected void keyPressed(char chr, int key) {
		if (key==Keyboard.KEY_ESCAPE) {
			if (this.modal!=null) { this.modal=null; rebuild(); } else { leave(); }
			return;
		}
		if (this.modal==null && key==Keyboard.KEY_F && isControlDown()) { this.search.setFocused(true); this.focusId=-1; return; }
		if (key==Keyboard.KEY_TAB) {
			this.keyboardFocus=true;
			List<ButtonWidget> available=this.buttons.stream().filter(b -> b.active && b.visible).toList();
			if (available.isEmpty()) { return; }
			int index=-1;
			for (int i=0;i<available.size();i++) { if (available.get(i).id==this.focusId) { index=i; } }
			index=Math.floorMod(index+(isShiftDown()?-1:1),available.size());
			this.focusId=available.get(index).id; this.search.setFocused(false); return;
		}
		if (this.modal==null && this.search.isFocused()) {
			if (this.search.keyPressed(chr,key)) {
				this.query=this.search.getText(); this.offset=0; rebuild();
			}
			return;
		}
		ButtonWidget focused=this.buttons.stream().filter(b -> b.id==this.focusId && b.active).findFirst().orElse(null);
		if (focused instanceof SettingButton row && (key==Keyboard.KEY_LEFT || key==Keyboard.KEY_RIGHT)) {
			this.draft.cycle(row.setting,key==Keyboard.KEY_LEFT?-1:1); refreshApply(); return;
		}
		if (focused!=null && (key==Keyboard.KEY_RETURN || key==Keyboard.KEY_SPACE || key==Keyboard.KEY_NUMPADENTER)) {
			if (focused instanceof SettingButton row) { this.draft.cycle(row.setting,1); refreshApply(); }
			else { buttonClicked(focused); }
			return;
		}
		if (this.modal==null && (key==Keyboard.KEY_NEXT || key==Keyboard.KEY_PRIOR)) {
			this.offset+=(key==Keyboard.KEY_NEXT?1:-1)*this.pageSize; this.focusId=-1; rebuild();
		}
	}
	@Override public void render(int mouseX, int mouseY, float tickDelta) {
		this.renderBackground();
		this.drawCenteredString(this.textRenderer,"Video Settings",this.width/2,8,TEXT);
		String subtitle="\u00a77"+(this.query.isEmpty()?this.category.description:"Search results across all categories");
		this.drawCenteredString(this.textRenderer,trim(subtitle,this.width-20),this.width/2,23,MUTED);
		this.search.render();
		if (this.search.getText().isEmpty() && !this.search.isFocused()) {
			this.textRenderer.drawWithShadow("Search settings...",this.left+PRESETS_WIDTH+8,this.top-20,0x808080);
		}
		if (this.matches.isEmpty()) {
			this.drawCenteredString(this.textRenderer,"No settings found",this.left+this.contentWidth/2,this.top+22,TEXT);
			this.drawCenteredString(this.textRenderer,"Try a different search.",this.left+this.contentWidth/2,this.top+38,MUTED);
		}
		super.render(mouseX,mouseY,tickDelta);
		if (this.matches.size()>this.pageSize) {
			int track=this.pageSize/2*ROW_HEIGHT-4;
			int sx=this.left+this.contentWidth+4;
			fill(sx,this.top,sx+6,this.top+track,0xff000000);
			int thumb=Math.max(12,track*this.pageSize/this.matches.size());
			int sy=this.top+(track-thumb)*this.offset/maxOffset();
			fill(sx,sy,sx+6,sy+thumb,0xff808080);
			fill(sx,sy,sx+5,sy+thumb-1,0xffc0c0c0);
		}
		String message=this.status;
		if (message.isEmpty()) {
			int changed=this.draft.changes().size();
			message=changed>0?"\u00a7e"+changed+" unsaved change"+(changed==1?"":"s"):this.backend.restartRequired()?"\u00a76Restart required for saved changes":"\u00a77Changes are saved when you Apply or select Done.";
		}
		this.drawCenteredString(this.textRenderer,trim(message,this.width-20),this.width/2,this.height-43,MUTED);
		if (this.modal!=null) { renderModal(mouseX,mouseY); return; }
		SettingButton hovered=this.rows.stream().filter(r -> r.contains(mouseX,mouseY) || this.keyboardFocus && r.id==this.focusId).findFirst().orElse(null);
		if (hovered==null) { this.hoverKey=null; }
		else {
			String key=hovered.setting.key();
			if (!key.equals(this.hoverKey)) { this.hoverKey=key; this.hoverSince=System.nanoTime(); }
			if (System.nanoTime()-this.hoverSince>450_000_000L || this.keyboardFocus && hovered.id==this.focusId) {
				List<String> help=new ArrayList<>(); help.add("\u00a76\u00a7l"+hovered.setting.title());
				help.addAll(this.textRenderer.split("\u00a77"+hovered.setting.description(),Math.min(260,this.width-50)));
				help.add("\u00a78Default: \u00a77"+hovered.setting.valueLabel(hovered.setting.defaultValue()));
				help.add((hovered.setting.effect()==VideoSetting.Effect.RESTART?"\u00a76":"\u00a77")+hovered.setting.effect().label);
				if (hovered.setting.experimental()) { help.add("\u00a7c! Experimental: change only if you can test the result"); }
				String note=this.backend.note(hovered.setting);
				if (note!=null) { help.addAll(this.textRenderer.split("\u00a77"+note,Math.min(260,this.width-50))); }
				String locked=this.backend.lockedReason(hovered.setting);
				if (locked!=null) { help.addAll(this.textRenderer.split("\u00a7e"+locked,Math.min(260,this.width-50))); }
				drawHelp(help,mouseX,mouseY);
			}
		}
	}
	/** Use Minecraft's tooltip renderer, with its anchor constrained to the visible screen. */
	private void drawHelp(List<String> lines,int mouseX,int mouseY) {
		int w=0;
		for (String line : lines) { w=Math.max(w,this.textRenderer.getWidth(line)); }
		int h=10*lines.size();
		int x=Math.max(8,Math.min(this.width-w-8,mouseX+12));
		int y=Math.max(8,Math.min(this.height-h-8,mouseY+16));
		this.renderTooltip(lines,x-12,y+12);
	}
	private void renderModal(int mouseX,int mouseY) {
		fill(0,0,this.width,this.height,0xb0000000);
		int x=this.width/2-134,y=this.height/2-54;
		String title=switch (this.modal) { case "reset" -> "Reset "+this.category.title+"?"; case "preset" -> "Stage a preset?"; default -> "Discard unsaved changes?"; };
		String body=switch (this.modal) {
			case "reset" -> "Only this page's settings will return to defaults. Apply to save the changes.";
			case "preset" -> "Stages graphics and detail choices only. Distance, display and advanced settings are kept. Apply to save.";
			default -> "Your pending edits will be discarded. Settings already applied will stay saved.";
		};
		this.drawCenteredString(this.textRenderer,"\u00a7e"+title,this.width/2,y+15,TEXT);
		List<String> lines=this.textRenderer.split(body,238);
		int cy=y+33;
		for (String line : lines) { this.drawCenteredString(this.textRenderer,line,this.width/2,cy,MUTED); cy+=11; }
		for (ButtonWidget b : this.buttons) { if (b.id==6 || b.id==7 || presetButton(b.id)) { b.render(this.minecraft,mouseX,mouseY); } }
		if ("preset".equals(this.modal)) {
			for (ButtonWidget b : this.buttons) {
				if (presetButton(b.id) && mouseX>=b.x && mouseX<b.x+b.getWidth() && mouseY>=b.y && mouseY<b.y+20) {
					drawHelp(this.textRenderer.split("\u00a77"+VideoSettingsPresets.values()[b.id-PRESET_BUTTONS].description,Math.min(260,this.width-50)),mouseX,mouseY);
				}
			}
		}
	}
	private static boolean presetButton(int id) { return id>=PRESET_BUTTONS && id<PRESET_BUTTONS+VideoSettingsPresets.values().length; }
	private String trim(String text, int width) {
		if (this.textRenderer.getWidth(text)<=width) { return text; }
		while (!text.isEmpty() && this.textRenderer.getWidth(text+"...")>width) { text=text.substring(0,text.length()-1); }
		if (text.endsWith("\u00a7")) { text=text.substring(0,text.length()-1); }
		return text+"...";
	}
	private class MenuButton extends ButtonWidget {
		private final boolean selected;
		MenuButton(int id,int x,int y,int width,int height,String label,boolean selected) { super(id,x,y,width,height,label); this.selected=selected; }
		@Override public void render(Minecraft mc,int mx,int my) {
			String label=this.message;
			this.message=(this.selected && this.active?"\u00a76":"")+trim(label,this.width-6);
			super.render(mc,keyboardFocus && this.id==focusId?this.x:mx,keyboardFocus && this.id==focusId?this.y:my);
			this.message=label;
		}
	}
	private final class SettingButton extends ButtonWidget {
		final VideoSetting setting;
		SettingButton(int id,VideoSetting setting,int x,int y,int width) { super(id,x,y,width,20,setting.title()); this.setting=setting; }
		boolean contains(int x,int y) { return x>=this.x && x<this.x+this.width && y>=this.y && y<this.y+this.height; }
		@Override public void render(Minecraft mc,int mx,int my) {
			String value=": "+(this.active?"\u00a7f":"\u00a78")+this.setting.valueLabel(draft.value(this.setting));
			String marker=(draft.changed(this.setting)?"\u00a7e* \u00a7r":"")+(this.setting.experimental()?"\u00a7c! \u00a7r":"");
			this.message=marker+trim(this.setting.title(),this.width-8-mc.textRenderer.getWidth(value+marker))+value;
			super.render(mc,keyboardFocus && this.id==focusId?this.x:mx,keyboardFocus && this.id==focusId?this.y:my);
		}
		@Override protected int getYImage(boolean hovered) { return this.setting.slider()?0:super.getYImage(hovered); }
		@Override protected void renderBackground(Minecraft mc,int mx,int my) {
			if (!this.setting.slider()) { return; }
			int value=draft.value(this.setting);
			int knob=this.x+(this.width-8)*(value-this.setting.minimum())/(this.setting.maximum()-this.setting.minimum());
			this.drawTexture(knob,this.y,0,66,4,20);
			this.drawTexture(knob+4,this.y,196,66,4,20);
		}
	}
}
