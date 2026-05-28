#!/usr/bin/env python3
"""
Concept board skin cutter for MathWorkbook.

Workflow:
1. Import one or more AI concept board images.
2. Pick a skin asset from the list.
3. Drag a rectangle over the source image.
4. Optionally pick a background color to make transparent.
5. Export a skin zip containing skin.json and assets/*.png.

The tool intentionally uses only tkinter and Pillow so it can run on the
bundled Codex Python runtime.
"""

from __future__ import annotations

import json
import zipfile
from dataclasses import dataclass, asdict
from pathlib import Path
from typing import Dict, List, Optional, Tuple

import tkinter as tk
from tkinter import filedialog, messagebox, ttk

from PIL import Image, ImageChops, ImageDraw, ImageTk


ASSET_SPECS = [
    ("dashboardBackground", "dashboard_bg_1600x2560.png", 1600, 2560, "Dashboard full background"),
    ("bookCoverBase", "book_cover_base_512x700.png", 512, 700, "Workbook cover"),
    ("bookCoverSpine", "book_cover_spine_96x700.png", 96, 700, "Workbook spine"),
    ("dashboardPageFrame", "dashboard_page_frame_1600x2400.png", 1600, 2400, "Dashboard page frame"),
    ("dashboardTitleBanner", "dashboard_title_banner_1200x220.png", 1200, 220, "Dashboard title banner"),
    ("dashboardTitleFrame", "dashboard_title_frame_1000x180.png", 1000, 180, "Dashboard title frame"),
    ("chapterRowTab", "chapter_row_tab_1200x180.png", 1200, 180, "Chapter row"),
    ("problemPaperBackground", "problem_paper_bg_1600x2560.png", 1600, 2560, "Problem paper background"),
    ("toolbarStrip", "toolbar_strip_1600x96.png", 1600, 96, "Problem toolbar strip"),
    ("problemHeaderPill", "problem_header_pill_900x96.png", 900, 96, "Problem title header"),
    ("masterButtonIdle", "master_button_idle_128x128.png", 128, 128, "Master button idle"),
    ("masterButtonActive", "master_button_active_128x128.png", 128, 128, "Master button active"),
    ("navArrowPrevious", "nav_arrow_previous_160x128.png", 160, 128, "Previous button with arrow"),
    ("navArrowNext", "nav_arrow_next_160x128.png", 160, 128, "Next button with arrow"),
    ("hintButton", "hint_button_128x128.png", 128, 128, "Hint button with icon"),
    ("submitButton", "submit_button_320x128.png", 320, 128, "Submit button visual state"),
    ("gradingButton", "grading_button_320x128.png", 320, 128, "Grading button visual state"),
    ("answerStampBlue", "answer_stamp_blue_512x180.png", 512, 180, "Answer stamp"),
    ("answerWrongSlash", "answer_wrong_slash_512x180.png", 512, 180, "Wrong-answer slash"),
]


SPEC_BY_KEY = {item[0]: item for item in ASSET_SPECS}
PREVIEW_MODES = ("Book Shelf", "Chapter List", "Problem")
PREVIEW_MODE_BY_ASSET = {
    "dashboardBackground": "Book Shelf",
    "dashboardPageFrame": "Book Shelf",
    "dashboardTitleBanner": "Book Shelf",
    "dashboardTitleFrame": "Book Shelf",
    "bookCoverBase": "Book Shelf",
    "bookCoverSpine": "Book Shelf",
    "chapterRowTab": "Chapter List",
    "problemPaperBackground": "Problem",
    "toolbarStrip": "Problem",
    "problemHeaderPill": "Problem",
    "navArrowPrevious": "Problem",
    "navArrowNext": "Problem",
    "hintButton": "Problem",
    "submitButton": "Problem",
    "gradingButton": "Problem",
    "answerStampBlue": "Problem",
    "answerWrongSlash": "Problem",
    "masterButtonIdle": "Book Shelf",
    "masterButtonActive": "Book Shelf",
}
LAYER_INFO_BY_ASSET = {
    "dashboardBackground": "Book Shelf layer 1: screen background below every dashboard element.",
    "dashboardPageFrame": "Book Shelf layer 2: transparent frame above dashboardBackground, below title/books.",
    "dashboardTitleBanner": "Book Shelf layer 3: title decoration behind the real title text.",
    "dashboardTitleFrame": "Book Shelf layer 3: title decoration behind the real title text.",
    "bookCoverBase": "Book Shelf/Chapter layer 4: workbook card artwork, below title/progress text.",
    "bookCoverSpine": "Book Shelf/Chapter layer 5: spine overlay above bookCoverBase.",
    "chapterRowTab": "Chapter List layer 3: row background below chapter text/progress.",
    "problemPaperBackground": "Problem layer 1: worksheet paper background below problem content and handwriting.",
    "toolbarStrip": "Problem layer 4: toolbar strip behind top navigation/tool buttons.",
    "problemHeaderPill": "Problem layer 5: header artwork behind title/chapter/problem number text.",
    "navArrowPrevious": "Problem layer 6: previous button image.",
    "navArrowNext": "Problem layer 6: next button image.",
    "hintButton": "Problem layer 6: hint button image.",
    "submitButton": "Problem layer 6: submit button image.",
    "gradingButton": "Problem layer 6: grading/loading button image.",
    "answerStampBlue": "Problem layer 5: submitted-answer stamp over problem area.",
    "answerWrongSlash": "Problem layer 6: wrong-answer mark over answer stamp.",
    "masterButtonIdle": "App overlay layer: bottom-right master button.",
    "masterButtonActive": "App overlay layer: bottom-right master button.",
}


@dataclass
class AssetRecipe:
    source_index: int = 0
    crop: Optional[Tuple[int, int, int, int]] = None
    polygon_points: Optional[List[Tuple[int, int]]] = None
    transparent_color: Optional[Tuple[int, int, int]] = None
    transparent_threshold: int = 26
    auto_corner_transparency: bool = False
    clip_shape: str = "none"


class ConceptSkinCutter(tk.Tk):
    def __init__(self) -> None:
        super().__init__()
        self.title("MathWorkbook Skin Concept Cutter")
        self.geometry("1440x920")
        self.minsize(1180, 760)

        self.images: List[Tuple[Path, Image.Image]] = []
        self.recipes: Dict[str, AssetRecipe] = {key: AssetRecipe() for key, *_ in ASSET_SPECS}
        self.selected_key = ASSET_SPECS[0][0]
        self.pick_transparency_mode = False

        self.source_canvas_image = None
        self.preview_canvas_image = None
        self.source_scale = 1.0
        self.source_offset = (0, 0)
        self.drag_start: Optional[Tuple[int, int]] = None
        self.drag_rect_id: Optional[int] = None
        self.drag_action: Optional[str] = None
        self.move_start: Optional[Tuple[int, int]] = None
        self.move_original_crop: Optional[Tuple[int, int, int, int]] = None
        self.move_original_polygon: Optional[List[Tuple[int, int]]] = None
        self.resize_handle: Optional[Tuple[str, object]] = None
        self.resize_original_crop: Optional[Tuple[int, int, int, int]] = None
        self.resize_original_polygon: Optional[List[Tuple[int, int]]] = None

        self.skin_id = tk.StringVar(value="paper_album_custom")
        self.display_name = tk.StringVar(value="Paper Album Custom")
        self.preview_mode = tk.StringVar(value=PREVIEW_MODES[0])
        self.crop_mode_var = tk.StringVar(value="Rectangle")
        self.threshold_var = tk.IntVar(value=26)
        self.lock_ratio_var = tk.BooleanVar(value=True)
        self.clip_shape_var = tk.StringVar(value="none")
        self.status = tk.StringVar(value="Import a concept image to begin.")

        self._build_ui()
        self.bind_all("<Delete>", self.delete_selected_cut)
        self.bind_all("<BackSpace>", self.delete_selected_cut)
        self._refresh_asset_list()

    def _build_ui(self) -> None:
        root = ttk.Frame(self, padding=8)
        root.pack(fill=tk.BOTH, expand=True)

        top = ttk.Frame(root)
        top.pack(fill=tk.X, pady=(0, 8))
        ttk.Button(top, text="Import concept images", command=self.import_images).pack(side=tk.LEFT)
        ttk.Button(top, text="Load project", command=self.load_project).pack(side=tk.LEFT, padx=(6, 0))
        ttk.Button(top, text="Save project", command=self.save_project).pack(side=tk.LEFT, padx=(6, 0))
        ttk.Button(top, text="Export skin zip", command=self.export_skin_zip).pack(side=tk.LEFT, padx=(16, 0))
        ttk.Label(top, textvariable=self.status).pack(side=tk.LEFT, padx=(16, 0))

        meta = ttk.Frame(root)
        meta.pack(fill=tk.X, pady=(0, 8))
        ttk.Label(meta, text="skinId").pack(side=tk.LEFT)
        ttk.Entry(meta, textvariable=self.skin_id, width=24).pack(side=tk.LEFT, padx=(4, 12))
        ttk.Label(meta, text="displayName").pack(side=tk.LEFT)
        ttk.Entry(meta, textvariable=self.display_name, width=34).pack(side=tk.LEFT, padx=(4, 12))
        ttk.Label(meta, text="Preview").pack(side=tk.LEFT)
        ttk.OptionMenu(meta, self.preview_mode, self.preview_mode.get(), *PREVIEW_MODES, command=lambda _: self.update_preview()).pack(side=tk.LEFT)

        body = ttk.Panedwindow(root, orient=tk.HORIZONTAL)
        body.pack(fill=tk.BOTH, expand=True)

        left = ttk.Frame(body)
        middle = ttk.Frame(body)
        right = ttk.Frame(body)
        body.add(left, weight=3)
        body.add(middle, weight=1)
        body.add(right, weight=2)

        self.source_canvas = tk.Canvas(left, bg="#222222", highlightthickness=0)
        self.source_canvas.pack(fill=tk.BOTH, expand=True)
        self.source_canvas.bind("<Configure>", lambda _event: self.update_source_canvas())
        self.source_canvas.bind("<ButtonPress-1>", self.on_source_press)
        self.source_canvas.bind("<B1-Motion>", self.on_source_drag)
        self.source_canvas.bind("<ButtonRelease-1>", self.on_source_release)

        asset_panel = ttk.Frame(middle)
        asset_panel.pack(fill=tk.BOTH, expand=True)
        ttk.Label(asset_panel, text="Assets").pack(anchor=tk.W)
        self.asset_list = tk.Listbox(asset_panel, exportselection=False, height=22)
        self.asset_list.pack(fill=tk.BOTH, expand=True, pady=(4, 8))
        self.asset_list.bind("<<ListboxSelect>>", self.on_asset_select)

        nav = ttk.Frame(asset_panel)
        nav.pack(fill=tk.X)
        ttk.Button(nav, text="Previous image", command=lambda: self.shift_source(-1)).pack(side=tk.LEFT)
        ttk.Button(nav, text="Next image", command=lambda: self.shift_source(1)).pack(side=tk.LEFT, padx=(6, 0))
        ttk.Checkbutton(nav, text="Lock asset ratio", variable=self.lock_ratio_var).pack(side=tk.LEFT, padx=(10, 0))

        ttk.Separator(asset_panel).pack(fill=tk.X, pady=8)
        self.asset_info = ttk.Label(asset_panel, text="", wraplength=260)
        self.asset_info.pack(anchor=tk.W, fill=tk.X)
        ttk.Button(asset_panel, text="Save selected asset PNG", command=self.save_selected_asset).pack(fill=tk.X, pady=(8, 0))
        crop_box = ttk.LabelFrame(asset_panel, text="Cut")
        crop_box.pack(fill=tk.X, pady=(8, 0))
        ttk.OptionMenu(
            crop_box,
            self.crop_mode_var,
            self.crop_mode_var.get(),
            "Rectangle",
            "Polygon",
            command=lambda _value: self.update_source_canvas(),
        ).pack(fill=tk.X, pady=(4, 0))
        ttk.Button(crop_box, text="New polygon", command=self.start_polygon_crop).pack(fill=tk.X, pady=(4, 0))
        ttk.Button(crop_box, text="Undo polygon point", command=self.undo_polygon_point).pack(fill=tk.X, pady=(4, 0))
        ttk.Button(crop_box, text="Clear cut", command=self.clear_selected_crop).pack(fill=tk.X, pady=(4, 0))
        ttk.Label(
            crop_box,
            text="Rectangle: drag to cut. Polygon: click points; 3+ points make a closed cut. Drag corners to resize, drag edges to move, press Del to clear.",
            wraplength=260,
        ).pack(anchor=tk.W, pady=(4, 0))

        shape_box = ttk.LabelFrame(asset_panel, text="Clip shape")
        shape_box.pack(fill=tk.X, pady=(10, 0))
        ttk.OptionMenu(
            shape_box,
            self.clip_shape_var,
            self.clip_shape_var.get(),
            "none",
            "rounded",
            "circle",
            "left_arrow",
            "right_arrow",
            command=self.on_clip_shape_change,
        ).pack(fill=tk.X, pady=(4, 0))
        ttk.Label(
            shape_box,
            text="PNG files are rectangular. Use transparency or a clip shape so round/arrow buttons do not show a square background.",
            wraplength=260,
        ).pack(anchor=tk.W, pady=(4, 0))

        alpha_box = ttk.LabelFrame(asset_panel, text="Transparency")
        alpha_box.pack(fill=tk.X, pady=(10, 0))
        ttk.Button(alpha_box, text="Pick transparent color from image", command=self.enable_transparency_pick).pack(fill=tk.X, pady=(4, 0))
        ttk.Button(alpha_box, text="Auto transparent from crop corners", command=self.auto_corner_transparency).pack(fill=tk.X, pady=(4, 0))
        ttk.Button(alpha_box, text="Clear transparency", command=self.clear_transparency).pack(fill=tk.X, pady=(4, 0))
        ttk.Label(alpha_box, text="Threshold").pack(anchor=tk.W, pady=(8, 0))
        threshold = ttk.Scale(alpha_box, from_=0, to=100, orient=tk.HORIZONTAL, command=self.on_threshold_change)
        threshold.set(self.threshold_var.get())
        threshold.pack(fill=tk.X)

        self.preview_canvas = tk.Canvas(right, bg="#eeeeee", highlightthickness=0)
        self.preview_canvas.pack(fill=tk.BOTH, expand=True)
        self.preview_canvas.bind("<Configure>", lambda _event: self.update_preview())

    def _refresh_asset_list(self) -> None:
        self.asset_list.delete(0, tk.END)
        for key, filename, width, height, label in ASSET_SPECS:
            marker = "*" if self.has_cut(self.recipes[key]) else " "
            self.asset_list.insert(tk.END, f"{marker} {key}  {width}x{height}")
        self.asset_list.selection_clear(0, tk.END)
        index = [item[0] for item in ASSET_SPECS].index(self.selected_key)
        self.asset_list.selection_set(index)
        self.asset_list.see(index)
        self.update_asset_info()

    def update_asset_info(self) -> None:
        key, filename, width, height, label = SPEC_BY_KEY[self.selected_key]
        recipe = self.recipes[key]
        source = "none"
        if self.images and 0 <= recipe.source_index < len(self.images):
            source = self.images[recipe.source_index][0].name
        color = recipe.transparent_color if recipe.transparent_color else "none"
        polygon_count = len(recipe.polygon_points or [])
        layer_info = LAYER_INFO_BY_ASSET.get(key, "Layer info is not defined.")
        self.asset_info.configure(
            text=f"{label}\nfile: {filename}\nsize: {width} x {height}\nsource: {source}\nrect: {recipe.crop}\npolygon points: {polygon_count}\ntransparent: {color}\nclip: {recipe.clip_shape}\n{layer_info}"
        )
        self.threshold_var.set(recipe.transparent_threshold)
        self.clip_shape_var.set(recipe.clip_shape)

    def import_images(self) -> None:
        paths = filedialog.askopenfilenames(
            title="Import concept images",
            filetypes=[("Images", "*.png;*.jpg;*.jpeg;*.webp"), ("All files", "*.*")]
        )
        if not paths:
            return
        for raw in paths:
            path = Path(raw)
            image = Image.open(path).convert("RGBA")
            self.images.append((path, image))
        for recipe in self.recipes.values():
            if recipe.source_index >= len(self.images):
                recipe.source_index = 0
        self.status.set(f"Imported {len(paths)} image(s). Drag a crop rectangle.")
        self.update_source_canvas()
        self.update_preview()

    def on_asset_select(self, _event=None) -> None:
        selection = self.asset_list.curselection()
        if not selection:
            return
        self.selected_key = ASSET_SPECS[selection[0]][0]
        self.preview_mode.set(PREVIEW_MODE_BY_ASSET.get(self.selected_key, PREVIEW_MODES[0]))
        self.update_asset_info()
        self.update_source_canvas()
        self.update_preview()

    def has_cut(self, recipe: AssetRecipe) -> bool:
        return recipe.crop is not None or bool(recipe.polygon_points)

    def current_asset_ratio(self) -> float:
        _key, _filename, width, height, _label = SPEC_BY_KEY[self.selected_key]
        return width / height

    def adjust_point_for_aspect(self, start: Tuple[int, int], point: Tuple[int, int]) -> Tuple[int, int]:
        if not self.lock_ratio_var.get():
            return point
        x1, y1 = start
        x2, y2 = point
        dx = x2 - x1
        dy = y2 - y1
        if dx == 0 or dy == 0:
            return point
        ratio = self.current_asset_ratio()
        width = abs(dx)
        height = abs(dy)
        if width / height > ratio:
            width = int(height * ratio)
        else:
            height = int(width / ratio)
        return (x1 + (width if dx >= 0 else -width), y1 + (height if dy >= 0 else -height))

    def current_source(self) -> Optional[Image.Image]:
        if not self.images:
            return None
        recipe = self.recipes[self.selected_key]
        recipe.source_index = max(0, min(recipe.source_index, len(self.images) - 1))
        return self.images[recipe.source_index][1]

    def shift_source(self, delta: int) -> None:
        if not self.images:
            return
        recipe = self.recipes[self.selected_key]
        recipe.source_index = (recipe.source_index + delta) % len(self.images)
        self.update_asset_info()
        self.update_source_canvas()
        self.update_preview()

    def update_source_canvas(self) -> None:
        self.source_canvas.delete("all")
        image = self.current_source()
        if image is None:
            self.source_canvas.create_text(30, 30, anchor=tk.NW, fill="white", text="Import concept images first.")
            return
        canvas_w = max(1, self.source_canvas.winfo_width())
        canvas_h = max(1, self.source_canvas.winfo_height())
        scale = min(canvas_w / image.width, canvas_h / image.height)
        scale = min(scale, 1.0)
        display_w = max(1, int(image.width * scale))
        display_h = max(1, int(image.height * scale))
        display = image.resize((display_w, display_h), Image.Resampling.LANCZOS)
        self.source_canvas_image = ImageTk.PhotoImage(display)
        offset_x = (canvas_w - display_w) // 2
        offset_y = (canvas_h - display_h) // 2
        self.source_scale = scale
        self.source_offset = (offset_x, offset_y)
        self.source_canvas.create_image(offset_x, offset_y, anchor=tk.NW, image=self.source_canvas_image)
        self.draw_selected_cut()

    def draw_selected_cut(self) -> None:
        recipe = self.recipes[self.selected_key]
        sx, sy = self.source_offset
        s = self.source_scale
        if recipe.polygon_points:
            points = [(sx + x * s, sy + y * s) for x, y in recipe.polygon_points]
            if len(points) >= 2:
                line_points = points + ([points[0]] if len(points) >= 3 else [])
                self.source_canvas.create_line(*line_points, fill="#42a5ff", width=3, dash=(8, 4))
            for x, y in points:
                self.source_canvas.create_oval(x - 4, y - 4, x + 4, y + 4, fill="#ffcc33", outline="#202020")
            return
        if recipe.crop:
            x1, y1, x2, y2 = recipe.crop
            self.source_canvas.create_rectangle(
                sx + x1 * s,
                sy + y1 * s,
                sx + x2 * s,
                sy + y2 * s,
                outline="#42a5ff",
                width=3,
                dash=(8, 4),
            )
            handle_size = 5
            for hx, hy in [(x1, y1), (x2, y1), (x1, y2), (x2, y2)]:
                cx = sx + hx * s
                cy = sy + hy * s
                self.source_canvas.create_rectangle(
                    cx - handle_size,
                    cy - handle_size,
                    cx + handle_size,
                    cy + handle_size,
                    fill="#ffcc33",
                    outline="#202020",
                )

    def canvas_to_image_point(self, x: int, y: int) -> Optional[Tuple[int, int]]:
        image = self.current_source()
        if image is None:
            return None
        ox, oy = self.source_offset
        px = int((x - ox) / self.source_scale)
        py = int((y - oy) / self.source_scale)
        if px < 0 or py < 0 or px >= image.width or py >= image.height:
            return None
        return px, py

    def on_source_press(self, event) -> None:
        point = self.canvas_to_image_point(event.x, event.y)
        if point is None:
            return
        if self.pick_transparency_mode:
            self.pick_transparency_at(point)
            return
        resize_handle = self.hit_resize_handle(point)
        if resize_handle is not None:
            self.drag_action = "resize"
            self.resize_handle = resize_handle
            recipe = self.recipes[self.selected_key]
            self.resize_original_crop = recipe.crop
            self.resize_original_polygon = list(recipe.polygon_points or [])
            return
        if self.point_hits_current_cut(point):
            self.drag_action = "move"
            self.move_start = point
            recipe = self.recipes[self.selected_key]
            self.move_original_crop = recipe.crop
            self.move_original_polygon = list(recipe.polygon_points or [])
            return
        if self.crop_mode_var.get() == "Polygon":
            recipe = self.recipes[self.selected_key]
            points = list(recipe.polygon_points or [])
            points.append(point)
            recipe.polygon_points = points
            recipe.crop = None
            self.status.set(f"Polygon point {len(points)} saved for {self.selected_key}.")
            self._refresh_asset_list()
            self.update_source_canvas()
            self.update_preview()
            return
        self.drag_action = "rect"
        self.drag_start = point
        if self.drag_rect_id is not None:
            self.source_canvas.delete(self.drag_rect_id)
            self.drag_rect_id = None

    def on_source_drag(self, event) -> None:
        if self.drag_action == "resize":
            point = self.canvas_to_image_point(event.x, event.y)
            if point is None:
                return
            self.resize_current_cut(point)
            return
        if self.drag_action == "move":
            point = self.canvas_to_image_point(event.x, event.y)
            if point is None:
                return
            self.move_current_cut(point)
            return
        if self.drag_action != "rect" or self.drag_start is None:
            return
        point = self.canvas_to_image_point(event.x, event.y)
        if point is None:
            return
        point = self.adjust_point_for_aspect(self.drag_start, point)
        x1, y1 = self.drag_start
        x2, y2 = point
        ox, oy = self.source_offset
        s = self.source_scale
        if self.drag_rect_id is not None:
            self.source_canvas.delete(self.drag_rect_id)
        self.drag_rect_id = self.source_canvas.create_rectangle(
            ox + x1 * s,
            oy + y1 * s,
            ox + x2 * s,
            oy + y2 * s,
            outline="#ffcc33",
            width=2,
        )

    def on_source_release(self, event) -> None:
        if self.drag_action == "resize":
            self.drag_action = None
            self.resize_handle = None
            self.resize_original_crop = None
            self.resize_original_polygon = None
            self._refresh_asset_list()
            self.update_source_canvas()
            self.update_preview()
            return
        if self.drag_action == "move":
            self.drag_action = None
            self.move_start = None
            self.move_original_crop = None
            self.move_original_polygon = None
            self._refresh_asset_list()
            self.update_source_canvas()
            self.update_preview()
            return
        if self.drag_action != "rect" or self.drag_start is None:
            return
        point = self.canvas_to_image_point(event.x, event.y)
        if point is None:
            self.drag_start = None
            return
        point = self.adjust_point_for_aspect(self.drag_start, point)
        x1, y1 = self.drag_start
        x2, y2 = point
        left, right = sorted((x1, x2))
        top, bottom = sorted((y1, y2))
        if right - left >= 8 and bottom - top >= 8:
            recipe = self.recipes[self.selected_key]
            recipe.crop = (left, top, right, bottom)
            recipe.polygon_points = None
            self.status.set(f"Crop saved for {self.selected_key}.")
        self.drag_start = None
        self.drag_action = None
        self._refresh_asset_list()
        self.update_source_canvas()
        self.update_preview()

    def hit_resize_handle(self, point: Tuple[int, int]) -> Optional[Tuple[str, object]]:
        recipe = self.recipes[self.selected_key]
        tolerance = max(6, int(10 / max(self.source_scale, 0.1)))
        if recipe.polygon_points:
            polygon_hits = [
                (point_distance(point, vertex), index)
                for index, vertex in enumerate(recipe.polygon_points)
                if point_distance(point, vertex) <= tolerance
            ]
            if polygon_hits:
                _distance, index = min(polygon_hits, key=lambda item: item[0])
                return ("polygon_vertex", index)
        if recipe.crop:
            x1, y1, x2, y2 = recipe.crop
            corners = {
                "nw": (x1, y1),
                "ne": (x2, y1),
                "sw": (x1, y2),
                "se": (x2, y2),
            }
            rect_hits = [
                (point_distance(point, corner), name)
                for name, corner in corners.items()
                if point_distance(point, corner) <= tolerance
            ]
            if rect_hits:
                _distance, name = min(rect_hits, key=lambda item: item[0])
                return ("rect_corner", name)
        return None

    def point_hits_current_cut(self, point: Tuple[int, int]) -> bool:
        recipe = self.recipes[self.selected_key]
        tolerance = max(4, int(8 / max(self.source_scale, 0.1)))
        if recipe.polygon_points and len(recipe.polygon_points) >= 3:
            return point_in_polygon(point, recipe.polygon_points) or point_near_polygon_edge(point, recipe.polygon_points, tolerance)
        if recipe.crop:
            x1, y1, x2, y2 = recipe.crop
            x, y = point
            inside = x1 <= x <= x2 and y1 <= y <= y2
            near_vertical = (abs(x - x1) <= tolerance or abs(x - x2) <= tolerance) and y1 - tolerance <= y <= y2 + tolerance
            near_horizontal = (abs(y - y1) <= tolerance or abs(y - y2) <= tolerance) and x1 - tolerance <= x <= x2 + tolerance
            return inside or near_vertical or near_horizontal
        return False

    def resize_current_cut(self, point: Tuple[int, int]) -> None:
        image = self.current_source()
        if image is None or self.resize_handle is None:
            return
        kind, handle = self.resize_handle
        recipe = self.recipes[self.selected_key]
        if kind == "polygon_vertex" and self.resize_original_polygon:
            index = int(handle)
            clamped = clamp_point(point, image.size)
            points = list(self.resize_original_polygon)
            if 0 <= index < len(points):
                points[index] = clamped
                recipe.polygon_points = points
                recipe.crop = None
        elif kind == "rect_corner" and self.resize_original_crop:
            resized = resize_rect_from_corner(
                rect=self.resize_original_crop,
                handle=str(handle),
                point=point,
                image_size=image.size,
                keep_ratio=self.lock_ratio_var.get(),
                ratio=self.current_asset_ratio(),
            )
            if resized is not None:
                recipe.crop = resized
                recipe.polygon_points = None
        self.update_source_canvas()
        self.update_preview()

    def move_current_cut(self, point: Tuple[int, int]) -> None:
        if self.move_start is None:
            return
        image = self.current_source()
        if image is None:
            return
        dx = point[0] - self.move_start[0]
        dy = point[1] - self.move_start[1]
        recipe = self.recipes[self.selected_key]
        if self.move_original_polygon:
            dx, dy = clamp_delta_for_points(self.move_original_polygon, dx, dy, image.size)
            recipe.polygon_points = [(x + dx, y + dy) for x, y in self.move_original_polygon]
            recipe.crop = None
        elif self.move_original_crop:
            x1, y1, x2, y2 = self.move_original_crop
            dx, dy = clamp_delta_for_rect(self.move_original_crop, dx, dy, image.size)
            recipe.crop = (x1 + dx, y1 + dy, x2 + dx, y2 + dy)
            recipe.polygon_points = None
        self.update_source_canvas()
        self.update_preview()

    def enable_transparency_pick(self) -> None:
        self.pick_transparency_mode = True
        self.status.set("Click a background color in the source image.")

    def pick_transparency_at(self, point: Tuple[int, int]) -> None:
        image = self.current_source()
        if image is None:
            return
        r, g, b, _a = image.getpixel(point)
        recipe = self.recipes[self.selected_key]
        recipe.transparent_color = (r, g, b)
        recipe.auto_corner_transparency = False
        self.pick_transparency_mode = False
        self.status.set(f"Transparent color for {self.selected_key}: {(r, g, b)}")
        self.update_asset_info()
        self.update_preview()

    def auto_corner_transparency(self) -> None:
        recipe = self.recipes[self.selected_key]
        crop = self.render_raw_crop(self.selected_key)
        if crop is None:
            messagebox.showwarning("No crop", "Select a crop rectangle first.")
            return
        points = [
            crop.getpixel((0, 0)),
            crop.getpixel((crop.width - 1, 0)),
            crop.getpixel((0, crop.height - 1)),
            crop.getpixel((crop.width - 1, crop.height - 1)),
        ]
        r = sum(p[0] for p in points) // 4
        g = sum(p[1] for p in points) // 4
        b = sum(p[2] for p in points) // 4
        recipe.transparent_color = (r, g, b)
        recipe.auto_corner_transparency = True
        self.status.set(f"Corner transparency set for {self.selected_key}.")
        self.update_asset_info()
        self.update_preview()

    def clear_transparency(self) -> None:
        recipe = self.recipes[self.selected_key]
        recipe.transparent_color = None
        recipe.auto_corner_transparency = False
        self.update_asset_info()
        self.update_preview()

    def on_threshold_change(self, value: str) -> None:
        recipe = self.recipes[self.selected_key]
        recipe.transparent_threshold = int(float(value))
        self.update_asset_info()
        self.update_preview()

    def on_clip_shape_change(self, value: str) -> None:
        self.recipes[self.selected_key].clip_shape = value
        self.update_asset_info()
        self.update_preview()

    def clear_selected_crop(self) -> None:
        recipe = self.recipes[self.selected_key]
        recipe.crop = None
        recipe.polygon_points = None
        self.status.set(f"Cleared cut for {self.selected_key}.")
        self._refresh_asset_list()
        self.update_source_canvas()
        self.update_preview()

    def delete_selected_cut(self, event=None) -> None:
        if event is not None and event.widget.winfo_class() in {"Entry", "TEntry", "Spinbox", "TSpinbox"}:
            return
        recipe = self.recipes[self.selected_key]
        if not self.has_cut(recipe):
            return
        self.clear_selected_crop()

    def start_polygon_crop(self) -> None:
        recipe = self.recipes[self.selected_key]
        recipe.crop = None
        recipe.polygon_points = []
        self.crop_mode_var.set("Polygon")
        self.status.set(f"Click points around {self.selected_key}. Three or more points make a polygon cut.")
        self._refresh_asset_list()
        self.update_source_canvas()
        self.update_preview()

    def undo_polygon_point(self) -> None:
        recipe = self.recipes[self.selected_key]
        if not recipe.polygon_points:
            return
        recipe.polygon_points = recipe.polygon_points[:-1]
        self.status.set(f"Polygon has {len(recipe.polygon_points)} point(s).")
        self._refresh_asset_list()
        self.update_source_canvas()
        self.update_preview()

    def render_raw_crop(self, key: str) -> Optional[Image.Image]:
        recipe = self.recipes[key]
        if not self.images:
            return None
        if recipe.source_index < 0 or recipe.source_index >= len(self.images):
            return None
        image = self.images[recipe.source_index][1]
        if recipe.polygon_points and len(recipe.polygon_points) >= 3:
            points = recipe.polygon_points
            left = max(0, min(x for x, _y in points))
            top = max(0, min(y for _x, y in points))
            right = min(image.width, max(x for x, _y in points))
            bottom = min(image.height, max(y for _x, y in points))
            if right - left < 2 or bottom - top < 2:
                return None
            crop_image = image.crop((left, top, right, bottom)).convert("RGBA")
            mask = Image.new("L", crop_image.size, 0)
            draw = ImageDraw.Draw(mask)
            draw.polygon([(x - left, y - top) for x, y in points], fill=255)
            crop_image.putalpha(ImageChops.multiply(crop_image.getchannel("A"), mask))
            return crop_image
        crop = recipe.crop
        if crop is None:
            return None
        return image.crop(crop).convert("RGBA")

    def render_asset(self, key: str) -> Optional[Image.Image]:
        spec = SPEC_BY_KEY[key]
        _key, _filename, width, height, _label = spec
        crop = self.render_raw_crop(key)
        if crop is None:
            return None
        crop = self.apply_transparency(crop, self.recipes[key])
        crop = crop.resize((width, height), Image.Resampling.LANCZOS)
        return self.apply_clip_shape(crop, self.recipes[key].clip_shape)

    def apply_transparency(self, image: Image.Image, recipe: AssetRecipe) -> Image.Image:
        if recipe.transparent_color is None:
            return image
        r0, g0, b0 = recipe.transparent_color
        threshold = recipe.transparent_threshold
        result = image.copy().convert("RGBA")
        diff = ImageChops.difference(result.convert("RGB"), Image.new("RGB", result.size, (r0, g0, b0)))
        keep_mask = diff.convert("L").point(lambda value: 0 if value <= threshold else 255, "L")
        result.putalpha(ImageChops.multiply(result.getchannel("A"), keep_mask))
        return result

    def apply_clip_shape(self, image: Image.Image, shape: str) -> Image.Image:
        if shape == "none":
            return image
        width, height = image.size
        mask = Image.new("L", image.size, 0)
        draw = ImageDraw.Draw(mask)
        if shape == "rounded":
            radius = max(8, min(width, height) // 8)
            draw.rounded_rectangle((0, 0, width - 1, height - 1), radius=radius, fill=255)
        elif shape == "circle":
            draw.ellipse((0, 0, width - 1, height - 1), fill=255)
        elif shape == "left_arrow":
            notch = max(16, width // 4)
            draw.polygon([(width - 1, 0), (notch, 0), (0, height // 2), (notch, height - 1), (width - 1, height - 1)], fill=255)
        elif shape == "right_arrow":
            notch = max(16, width // 4)
            draw.polygon([(0, 0), (width - notch - 1, 0), (width - 1, height // 2), (width - notch - 1, height - 1), (0, height - 1)], fill=255)
        else:
            return image
        result = image.copy().convert("RGBA")
        result.putalpha(ImageChops.multiply(result.getchannel("A"), mask))
        return result

    def save_selected_asset(self) -> None:
        rendered = self.render_asset(self.selected_key)
        if rendered is None:
            messagebox.showwarning("No crop", "Select a crop rectangle first.")
            return
        _key, filename, _w, _h, _label = SPEC_BY_KEY[self.selected_key]
        path = filedialog.asksaveasfilename(
            title="Save PNG",
            initialfile=filename,
            defaultextension=".png",
            filetypes=[("PNG", "*.png")]
        )
        if not path:
            return
        rendered.save(path, "PNG", optimize=True, compress_level=9)
        self.status.set(f"Saved {Path(path).name}.")

    def update_preview(self) -> None:
        if not hasattr(self, "preview_canvas"):
            return
        self.preview_canvas.delete("all")
        canvas_w = max(1, self.preview_canvas.winfo_width())
        canvas_h = max(1, self.preview_canvas.winfo_height())
        mode = self.preview_mode.get()
        if mode == "Chapter List":
            preview = self.compose_chapter_list_preview()
        elif mode == "Problem":
            preview = self.compose_problem_preview()
        else:
            preview = self.compose_book_shelf_preview()
        self.draw_selected_asset_highlight(preview, mode)
        preview.thumbnail((canvas_w, canvas_h), Image.Resampling.LANCZOS)
        self.preview_canvas_image = ImageTk.PhotoImage(preview)
        self.preview_canvas.create_image(canvas_w // 2, canvas_h // 2, image=self.preview_canvas_image)

    def asset_or_blank(self, key: str, fallback_size: Tuple[int, int], color=(255, 255, 255, 0)) -> Image.Image:
        asset = self.render_asset(key)
        if asset is not None:
            return asset
        return Image.new("RGBA", fallback_size, color)

    def dashboard_title_asset_key(self) -> str:
        if self.has_cut(self.recipes["dashboardTitleFrame"]):
            return "dashboardTitleFrame"
        return "dashboardTitleBanner"

    def composite_asset(
        self,
        canvas: Image.Image,
        key: str,
        rect: Tuple[int, int, int, int],
        fallback_color=(255, 255, 255, 0),
        opacity: float = 1.0,
    ) -> None:
        x1, y1, x2, y2 = rect
        width = max(1, x2 - x1)
        height = max(1, y2 - y1)
        asset = self.asset_or_blank(key, (width, height), fallback_color).resize((width, height), Image.Resampling.LANCZOS)
        if opacity < 1.0:
            asset = with_opacity(asset, opacity)
        canvas.alpha_composite(asset, (x1, y1))

    def selected_asset_rects(self, mode: str) -> List[Tuple[int, int, int, int]]:
        key = self.selected_key
        book_rects = [(100 + col * 210, 230 + row * 265, 228 + col * 210, 405 + row * 265) for row in range(2) for col in range(3)]
        book_spines = [(x1, y1, x1 + 24, y2) for x1, y1, _x2, y2 in book_rects]
        chapter_rows = [(80, 390 + index * 104, 720, 476 + index * 104) for index in range(7)]
        common_dashboard = {
            "dashboardBackground": [(0, 0, 800, 1280)],
            "dashboardPageFrame": [(0, 0, 800, 1280)],
            "dashboardTitleBanner": [(100, 42, 700, 154)],
            "dashboardTitleFrame": [(100, 42, 700, 154)],
            "masterButtonIdle": [(724, 1194, 772, 1242)],
            "masterButtonActive": [(724, 1194, 772, 1242)],
        }
        if mode == "Book Shelf":
            rects = {
                **common_dashboard,
                "bookCoverBase": book_rects,
                "bookCoverSpine": book_spines,
            }
            return rects.get(key, [])
        if mode == "Chapter List":
            rects = {
                "dashboardBackground": [(0, 0, 800, 1280)],
                "dashboardTitleBanner": [(140, 50, 660, 144)],
                "dashboardTitleFrame": [(140, 50, 660, 144)],
                "masterButtonIdle": [(724, 1194, 772, 1242)],
                "masterButtonActive": [(724, 1194, 772, 1242)],
                "bookCoverBase": [(96, 178, 214, 340)],
                "bookCoverSpine": [(96, 178, 118, 340)],
                "chapterRowTab": chapter_rows,
            }
            return rects.get(key, [])
        if mode == "Problem":
            rects = {
                "problemPaperBackground": [(42, 68, 758, 1178)],
                "toolbarStrip": [(42, 70, 758, 118)],
                "problemHeaderPill": [(175, 84, 625, 132)],
                "navArrowPrevious": [(58, 82, 122, 133)],
                "navArrowNext": [(678, 82, 742, 133)],
                "hintButton": [(682, 170, 734, 222)],
                "submitButton": [(470, 1010, 620, 1070)],
                "gradingButton": [(470, 1085, 620, 1145)],
                "answerStampBlue": [(510, 348, 664, 402)],
                "answerWrongSlash": [(510, 348, 664, 402)],
                "masterButtonIdle": [(724, 1194, 772, 1242)],
                "masterButtonActive": [(724, 1194, 772, 1242)],
            }
            return rects.get(key, [])
        return []

    def draw_selected_asset_highlight(self, canvas: Image.Image, mode: str) -> None:
        rects = self.selected_asset_rects(mode)
        if not rects:
            self.draw_selected_asset_chip(canvas)
            return
        overlay = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
        draw = ImageDraw.Draw(overlay, "RGBA")
        for x1, y1, x2, y2 in rects:
            outline = (255, 136, 0, 230)
            radius = 18 if min(x2 - x1, y2 - y1) > 70 else 10
            draw.rounded_rectangle((x1, y1, x2, y2), radius=radius, outline=outline, width=4)
        canvas.alpha_composite(overlay)
        label_draw = ImageDraw.Draw(canvas)
        x1, y1, _x2, _y2 = rects[0]
        label = f"selected: {self.selected_key}"
        label_y = max(8, y1 - 28)
        label_draw.rounded_rectangle((x1, label_y, min(790, x1 + 260), label_y + 24), radius=6, fill=(255, 244, 189, 235), outline=(255, 136, 0, 210))
        label_draw.text((x1 + 8, label_y + 5), label, fill=(70, 45, 0, 255))
        self.draw_selected_asset_chip(canvas)

    def draw_selected_asset_chip(self, canvas: Image.Image) -> None:
        asset = self.render_asset(self.selected_key)
        if asset is None:
            return
        card_x, card_y, card_w, card_h = 28, canvas.height - 188, 236, 154
        chip = Image.new("RGBA", (card_w, card_h), (255, 255, 255, 235))
        checker = checkerboard((card_w - 20, card_h - 48), cell=10)
        chip.alpha_composite(checker, (10, 30))
        preview = asset.copy()
        preview.thumbnail((card_w - 34, card_h - 62), Image.Resampling.LANCZOS)
        px = 10 + (card_w - 20 - preview.width) // 2
        py = 30 + (card_h - 48 - preview.height) // 2
        chip.alpha_composite(preview, (px, py))
        chip_draw = ImageDraw.Draw(chip)
        chip_draw.rounded_rectangle((0, 0, card_w - 1, card_h - 1), radius=10, outline=(255, 136, 0, 220), width=3)
        chip_draw.text((12, 9), "selected asset alpha preview", fill=(70, 45, 0, 255))
        canvas.alpha_composite(chip, (card_x, card_y))

    def compose_book_shelf_preview(self) -> Image.Image:
        canvas = Image.new("RGBA", (800, 1280), (247, 248, 250, 255))
        self.composite_asset(canvas, "dashboardBackground", (0, 0, 800, 1280), (247, 248, 250, 255), opacity=0.82)
        self.composite_asset(canvas, "dashboardPageFrame", (0, 0, 800, 1280), opacity=0.94)
        self.composite_asset(canvas, self.dashboard_title_asset_key(), (100, 42, 700, 154), opacity=0.98)
        draw = ImageDraw.Draw(canvas)
        draw.rounded_rectangle((116, 50, 684, 144), radius=18, outline=(167, 139, 250, 40), width=1)
        draw.text((275, 88), "Workbook Title Area", fill=(65, 52, 42, 255))
        for row in range(2):
            for col in range(3):
                x = 100 + col * 210
                y = 230 + row * 265
                draw.rounded_rectangle((x - 2, y - 2, x + 130, y + 177), radius=8, fill=(253, 247, 236, 255), outline=(216, 198, 163, 255))
                self.composite_asset(canvas, "bookCoverBase", (x, y, x + 128, y + 175), (253, 247, 236, 255))
                draw.rectangle((x, y, x + 24, y + 175), fill=(110, 127, 167, 255), outline=(77, 93, 133, 255))
                self.composite_asset(canvas, "bookCoverSpine", (x, y, x + 24, y + 175))
                draw.rounded_rectangle((x + 34, y + 42, x + 112, y + 96), radius=8, outline=(90, 90, 90, 80))
                draw.text((x + 38, y + 60), "Book", fill=(40, 40, 40, 180))
        self.draw_master_button(canvas)
        return canvas

    def compose_chapter_list_preview(self) -> Image.Image:
        canvas = Image.new("RGBA", (800, 1280), (247, 248, 250, 255))
        self.composite_asset(canvas, "dashboardBackground", (0, 0, 800, 1280), (247, 248, 250, 255), opacity=0.82)
        self.composite_asset(canvas, self.dashboard_title_asset_key(), (140, 50, 660, 144), opacity=0.98)
        draw = ImageDraw.Draw(canvas)
        draw.rounded_rectangle((96, 178, 214, 340), radius=8, fill=(253, 247, 236, 255), outline=(216, 198, 163, 255))
        self.composite_asset(canvas, "bookCoverBase", (96, 178, 214, 340), (253, 247, 236, 255))
        draw.rectangle((96, 178, 118, 340), fill=(110, 127, 167, 255), outline=(77, 93, 133, 255))
        self.composite_asset(canvas, "bookCoverSpine", (96, 178, 118, 340))
        draw.text((272, 84), "Book Title Area", fill=(65, 52, 42, 255))
        draw.text((246, 214), "Selected workbook summary", fill=(40, 40, 40, 180))
        for index in range(7):
            x = 80
            y = 390 + index * 104
            draw.rounded_rectangle((x, y, x + 640, y + 86), radius=8, fill=(255, 255, 255, 255), outline=(225, 229, 235, 255))
            self.composite_asset(canvas, "chapterRowTab", (x, y, x + 640, y + 86), opacity=0.24)
            draw.text((126, y + 28), f"Chapter {index + 1}", fill=(40, 40, 40, 190))
            draw.rounded_rectangle((520, y + 32, 672, y + 46), radius=7, outline=(90, 90, 90, 70), fill=(255, 255, 255, 120))
            fill_w = 24 + index * 16
            draw.rounded_rectangle((520, y + 32, 520 + fill_w, y + 46), radius=7, fill=(112, 164, 132, 145))
            draw.text((684, y + 25), f"{index + 2}/10", fill=(40, 40, 40, 150))
        self.draw_master_button(canvas)
        return canvas

    def compose_problem_preview(self) -> Image.Image:
        canvas = Image.new("RGBA", (800, 1280), (247, 248, 250, 255))
        draw = ImageDraw.Draw(canvas)
        draw.rounded_rectangle((30, 54, 770, 1190), radius=16, fill=(255, 255, 255, 255), outline=(225, 229, 235, 255))
        draw.rounded_rectangle((42, 68, 758, 1178), radius=8, fill=(250, 250, 250, 255))
        for y in range(160, 1060, 42):
            draw.line((54, y, 746, y), fill=(229, 231, 235, 255), width=1)
        self.composite_asset(canvas, "problemPaperBackground", (42, 68, 758, 1178), (250, 250, 250, 255), opacity=0.34)
        self.composite_asset(canvas, "toolbarStrip", (42, 70, 758, 118), opacity=0.90)
        draw.rounded_rectangle((90, 180, 710, 430), radius=16, fill=(255, 255, 255, 230), outline=(180, 180, 180, 120))
        draw.text((120, 230), "Problem image/text area", fill=(40, 40, 40, 180))
        draw.rounded_rectangle((500, 336, 674, 414), radius=12, outline=(120, 120, 120, 90), fill=(255, 255, 255, 120))
        self.composite_asset(canvas, "answerStampBlue", (510, 348, 664, 402))
        self.composite_asset(canvas, "answerWrongSlash", (510, 348, 664, 402))
        for y in range(520, 900, 42):
            draw.line((100, y, 700, y), fill=(130, 130, 130, 45), width=1)
        self.composite_asset(canvas, "problemHeaderPill", (175, 84, 625, 132))
        self.composite_asset(canvas, "navArrowPrevious", (58, 82, 122, 133))
        self.composite_asset(canvas, "navArrowNext", (678, 82, 742, 133))
        self.composite_asset(canvas, "hintButton", (682, 170, 734, 222))
        self.composite_asset(canvas, "submitButton", (470, 1010, 620, 1070))
        self.composite_asset(canvas, "gradingButton", (470, 1085, 620, 1145))
        self.draw_master_button(canvas)
        return canvas

    def draw_master_button(self, canvas: Image.Image) -> None:
        key = "masterButtonActive" if self.selected_key == "masterButtonActive" else "masterButtonIdle"
        master = self.asset_or_blank(key, (128, 128), (255, 255, 255, 0)).resize((48, 48))
        canvas.alpha_composite(master, (724, 1194))

    def compose_buttons_preview(self) -> Image.Image:
        canvas = checkerboard((800, 700))
        draw = ImageDraw.Draw(canvas)
        keys = [
            "masterButtonIdle", "masterButtonActive", "navArrowPrevious", "navArrowNext",
            "hintButton", "submitButton", "gradingButton", "answerStampBlue", "answerWrongSlash",
        ]
        x, y = 40, 50
        for key in keys:
            asset = self.render_asset(key)
            draw.text((x, y - 20), key, fill=(30, 30, 30, 255))
            if asset:
                preview = asset.copy()
                preview.thumbnail((180, 90), Image.Resampling.LANCZOS)
                canvas.alpha_composite(preview, (x, y))
            else:
                draw.rounded_rectangle((x, y, x + 160, y + 72), radius=8, outline=(120, 120, 120, 180), width=2)
                draw.text((x + 20, y + 25), "not cut", fill=(80, 80, 80, 200))
            x += 240
            if x > 600:
                x = 40
                y += 140
        return canvas

    def save_project(self) -> None:
        path = filedialog.asksaveasfilename(
            title="Save cutter project",
            defaultextension=".json",
            filetypes=[("JSON", "*.json")]
        )
        if not path:
            return
        data = {
            "skinId": self.skin_id.get(),
            "displayName": self.display_name.get(),
            "images": [str(path) for path, _img in self.images],
            "recipes": {key: asdict(recipe) for key, recipe in self.recipes.items()},
        }
        Path(path).write_text(json.dumps(data, indent=2), encoding="utf-8")
        self.status.set(f"Saved project {Path(path).name}.")

    def load_project(self) -> None:
        path = filedialog.askopenfilename(
            title="Load cutter project",
            filetypes=[("JSON", "*.json"), ("All files", "*.*")]
        )
        if not path:
            return
        data = json.loads(Path(path).read_text(encoding="utf-8"))
        self.skin_id.set(data.get("skinId", "paper_album_custom"))
        self.display_name.set(data.get("displayName", "Paper Album Custom"))
        self.images.clear()
        for raw in data.get("images", []):
            image_path = Path(raw)
            if image_path.exists():
                self.images.append((image_path, Image.open(image_path).convert("RGBA")))
        loaded = data.get("recipes", {})
        for key in self.recipes:
            raw_recipe = loaded.get(key, {})
            crop = raw_recipe.get("crop")
            polygon = raw_recipe.get("polygon_points")
            color = raw_recipe.get("transparent_color")
            self.recipes[key] = AssetRecipe(
                source_index=int(raw_recipe.get("source_index", 0)),
                crop=tuple(crop) if crop else None,
                polygon_points=[tuple(point) for point in polygon] if polygon else None,
                transparent_color=tuple(color) if color else None,
                transparent_threshold=int(raw_recipe.get("transparent_threshold", 26)),
                auto_corner_transparency=bool(raw_recipe.get("auto_corner_transparency", False)),
                clip_shape=str(raw_recipe.get("clip_shape", "none")),
            )
        self._refresh_asset_list()
        self.update_source_canvas()
        self.update_preview()
        self.status.set(f"Loaded project {Path(path).name}.")

    def export_skin_zip(self) -> None:
        if not self.images:
            messagebox.showwarning("No images", "Import concept images first.")
            return
        path = filedialog.asksaveasfilename(
            title="Export skin zip",
            initialfile=f"workbook_skin_{safe_id(self.skin_id.get())}.zip",
            defaultextension=".zip",
            filetypes=[("ZIP", "*.zip")]
        )
        if not path:
            return
        assets = {}
        rendered_assets = {}
        for key, filename, _w, _h, _label in ASSET_SPECS:
            image = self.render_asset(key)
            if image is None:
                continue
            relative = f"assets/{filename}"
            assets[key] = relative
            rendered_assets[relative] = image
        if not rendered_assets:
            messagebox.showwarning("No assets", "Cut at least one asset before exporting.")
            return
        manifest = {
            "skinId": safe_id(self.skin_id.get()),
            "displayName": self.display_name.get().strip() or self.skin_id.get(),
            "version": 2,
            "targetDevice": {"orientation": "portrait", "baseWidthPx": 1600, "baseHeightPx": 2560},
            "assets": assets,
        }
        recipe_data = {
            "images": [str(path) for path, _img in self.images],
            "recipes": {key: asdict(recipe) for key, recipe in self.recipes.items()},
        }
        with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as zf:
            zf.writestr("skin.json", json.dumps(manifest, indent=2, ensure_ascii=False))
            zf.writestr("cut_recipe.json", json.dumps(recipe_data, indent=2, ensure_ascii=False))
            for relative, image in rendered_assets.items():
                buffer = image_to_png_bytes(image)
                zf.writestr(relative, buffer)
        self.status.set(f"Exported {Path(path).name} with {len(rendered_assets)} asset(s).")


def image_to_png_bytes(image: Image.Image) -> bytes:
    from io import BytesIO

    output = BytesIO()
    image.save(output, "PNG", optimize=True, compress_level=9)
    return output.getvalue()


def with_opacity(image: Image.Image, opacity: float) -> Image.Image:
    opacity = max(0.0, min(1.0, opacity))
    result = image.copy().convert("RGBA")
    alpha = result.getchannel("A").point(lambda value: int(value * opacity), "L")
    result.putalpha(alpha)
    return result


def point_in_polygon(point: Tuple[int, int], polygon: List[Tuple[int, int]]) -> bool:
    x, y = point
    inside = False
    j = len(polygon) - 1
    for i in range(len(polygon)):
        xi, yi = polygon[i]
        xj, yj = polygon[j]
        denominator = yj - yi
        intersects = denominator != 0 and (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / denominator + xi
        if intersects:
            inside = not inside
        j = i
    return inside


def point_near_polygon_edge(point: Tuple[int, int], polygon: List[Tuple[int, int]], tolerance: int) -> bool:
    if len(polygon) < 2:
        return False
    closed = polygon + [polygon[0]]
    return any(point_segment_distance(point, closed[index], closed[index + 1]) <= tolerance for index in range(len(polygon)))


def point_distance(a: Tuple[int, int], b: Tuple[int, int]) -> float:
    return ((a[0] - b[0]) ** 2 + (a[1] - b[1]) ** 2) ** 0.5


def point_segment_distance(point: Tuple[int, int], start: Tuple[int, int], end: Tuple[int, int]) -> float:
    px, py = point
    x1, y1 = start
    x2, y2 = end
    dx = x2 - x1
    dy = y2 - y1
    if dx == 0 and dy == 0:
        return ((px - x1) ** 2 + (py - y1) ** 2) ** 0.5
    t = ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy)
    t = max(0.0, min(1.0, t))
    nearest_x = x1 + t * dx
    nearest_y = y1 + t * dy
    return ((px - nearest_x) ** 2 + (py - nearest_y) ** 2) ** 0.5


def clamp_delta_for_rect(rect: Tuple[int, int, int, int], dx: int, dy: int, image_size: Tuple[int, int]) -> Tuple[int, int]:
    x1, y1, x2, y2 = rect
    width, height = image_size
    dx = max(-x1, min(dx, width - x2))
    dy = max(-y1, min(dy, height - y2))
    return dx, dy


def clamp_delta_for_points(points: List[Tuple[int, int]], dx: int, dy: int, image_size: Tuple[int, int]) -> Tuple[int, int]:
    xs = [x for x, _y in points]
    ys = [y for _x, y in points]
    width, height = image_size
    dx = max(-min(xs), min(dx, width - max(xs)))
    dy = max(-min(ys), min(dy, height - max(ys)))
    return dx, dy


def clamp_point(point: Tuple[int, int], image_size: Tuple[int, int]) -> Tuple[int, int]:
    width, height = image_size
    return max(0, min(point[0], width - 1)), max(0, min(point[1], height - 1))


def resize_rect_from_corner(
    rect: Tuple[int, int, int, int],
    handle: str,
    point: Tuple[int, int],
    image_size: Tuple[int, int],
    keep_ratio: bool,
    ratio: float,
    min_size: int = 8,
) -> Optional[Tuple[int, int, int, int]]:
    x1, y1, x2, y2 = rect
    point = clamp_point(point, image_size)
    if handle == "nw":
        anchor = (x2, y2)
        moving = point
    elif handle == "ne":
        anchor = (x1, y2)
        moving = point
    elif handle == "sw":
        anchor = (x2, y1)
        moving = point
    elif handle == "se":
        anchor = (x1, y1)
        moving = point
    else:
        return None
    if keep_ratio:
        moving = adjust_corner_point_for_ratio(anchor, moving, ratio)
        moving = clamp_point(moving, image_size)
    left, right = sorted((anchor[0], moving[0]))
    top, bottom = sorted((anchor[1], moving[1]))
    if right - left < min_size or bottom - top < min_size:
        return rect
    return left, top, right, bottom


def adjust_corner_point_for_ratio(anchor: Tuple[int, int], point: Tuple[int, int], ratio: float) -> Tuple[int, int]:
    ax, ay = anchor
    px, py = point
    dx = px - ax
    dy = py - ay
    if dx == 0 or dy == 0:
        return point
    width = abs(dx)
    height = abs(dy)
    if width / height > ratio:
        width = int(height * ratio)
    else:
        height = int(width / ratio)
    return ax + (width if dx >= 0 else -width), ay + (height if dy >= 0 else -height)


def checkerboard(size: Tuple[int, int], cell: int = 16) -> Image.Image:
    image = Image.new("RGBA", size, (255, 255, 255, 255))
    draw = ImageDraw.Draw(image)
    for y in range(0, size[1], cell):
        for x in range(0, size[0], cell):
            if (x // cell + y // cell) % 2:
                draw.rectangle((x, y, x + cell - 1, y + cell - 1), fill=(230, 230, 230, 255))
    return image


def safe_id(value: str) -> str:
    cleaned = "".join(ch.lower() if ch.isalnum() else "_" for ch in value.strip())
    cleaned = "_".join(part for part in cleaned.split("_") if part)
    return cleaned or "paper_album_custom"


if __name__ == "__main__":
    app = ConceptSkinCutter()
    app.mainloop()
