/**
 * 类别配色与对比度自适应模块（FR-04 / FR-07 / FR-09 / AC-9 / AC-12）
 */
(function (root) {
  'use strict';

  var WorkCal = (root.WorkCal = root.WorkCal || {});

  // 28 种预设颜色（>= 24 种，覆盖深中浅及常见业务色，含 4 个默认主色）
  var PRESET_COLORS = [
    '#D93A3A', '#E11D48', '#EA580C', '#D97A12', '#EAB308', '#FACC15', '#84CC16',
    '#2E9E5B', '#059669', '#0D9488', '#06B6D4', '#0284C7', '#2B6CD4', '#4F46E5',
    '#7C3AED', '#9333EA', '#C026D3', '#DB2777', '#9F1239', '#B45309', '#A16207',
    '#D6C29F', '#94A3B8', '#64748B', '#475569', '#334155', '#1E293B', '#0F766E'
  ];

  function normalizeHex(hex) {
    if (!hex || typeof hex !== 'string') return '#64748B';
    var clean = hex.trim().replace(/^#/, '');
    if (/^[0-9a-fA-F]{3}$/.test(clean)) {
      clean = clean[0] + clean[0] + clean[1] + clean[1] + clean[2] + clean[2];
    }
    if (!/^[0-9a-fA-F]{6}$/.test(clean)) return '#64748B';
    return '#' + clean.toUpperCase();
  }

  function hexToRgb(hex) {
    var norm = normalizeHex(hex).slice(1);
    return {
      r: parseInt(norm.slice(0, 2), 16),
      g: parseInt(norm.slice(2, 4), 16),
      b: parseInt(norm.slice(4, 6), 16)
    };
  }

  function rgbToHex(r, g, b) {
    var clamp = function (v) {
      return Math.max(0, Math.min(255, Math.round(v)));
    };
    return (
      '#' +
      [clamp(r), clamp(g), clamp(b)]
        .map(function (n) {
          return n.toString(16).padStart(2, '0');
        })
        .join('')
        .toUpperCase()
    );
  }

  function rgbToHsl(r, g, b) {
    r /= 255;
    g /= 255;
    b /= 255;
    var max = Math.max(r, g, b);
    var min = Math.min(r, g, b);
    var h = 0;
    var s = 0;
    var l = (max + min) / 2;

    if (max !== min) {
      var d = max - min;
      s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
      switch (max) {
        case r:
          h = (g - b) / d + (g < b ? 6 : 0);
          break;
        case g:
          h = (b - r) / d + 2;
          break;
        default:
          h = (r - g) / d + 4;
      }
      h /= 6;
    }
    return { h: h * 360, s: s * 100, l: l * 100 };
  }

  function hslToHex(h, s, l) {
    h = ((h % 360) + 360) % 360;
    s = Math.max(0, Math.min(100, s)) / 100;
    l = Math.max(0, Math.min(100, l)) / 100;

    var c = (1 - Math.abs(2 * l - 1)) * s;
    var x = c * (1 - Math.abs(((h / 60) % 2) - 1));
    var m = l - c / 2;
    var r = 0;
    var g = 0;
    var b = 0;

    if (h < 60) { r = c; g = x; }
    else if (h < 120) { r = x; g = c; }
    else if (h < 180) { g = c; b = x; }
    else if (h < 240) { g = x; b = c; }
    else if (h < 300) { r = x; b = c; }
    else { r = c; b = x; }

    return rgbToHex((r + m) * 255, (g + m) * 255, (b + m) * 255);
  }

  function getRelativeLuminance(hex) {
    var rgb = hexToRgb(hex);
    var toLinear = function (c) {
      var v = c / 255;
      return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    };
    return 0.2126 * toLinear(rgb.r) + 0.7152 * toLinear(rgb.g) + 0.0722 * toLinear(rgb.b);
  }

  function getContrastRatio(hex1, hex2) {
    var l1 = getRelativeLuminance(hex1);
    var l2 = getRelativeLuminance(hex2);
    var lighter = Math.max(l1, l2);
    var darker = Math.min(l1, l2);
    return (lighter + 0.05) / (darker + 0.05);
  }

  /**
   * 浅色类别标题字色保色相压暗算法（FR-09 / AC-12）
   * - 默认 4 个主色（最小对比度 ~3.13）在白底上本就清晰，当对比度 >= minAcceptable 时保持原色
   * - 若是浅黄(#FACC15)、浅褐(#D6C29F)等白底不可读浅色，保持色相 h 不变，二分降低明度直至对比度 >= targetContrast
   */
  function getReadableTextColor(hex, minAcceptable, targetContrast) {
    var norm = normalizeHex(hex);
    var acceptable = typeof minAcceptable === 'number' ? minAcceptable : 3.0;
    var target = typeof targetContrast === 'number' ? targetContrast : 4.5;

    if (getContrastRatio(norm, '#FFFFFF') >= acceptable) {
      return norm;
    }

    var rgb = hexToRgb(norm);
    var hsl = rgbToHsl(rgb.r, rgb.g, rgb.b);
    // 若原先几乎无饱和度，保留灰阶；若有色相，略微维持饱和度避免压暗后发灰
    var sat = hsl.s > 8 ? Math.max(hsl.s, Math.min(82, hsl.s * 1.1)) : hsl.s;

    var low = 4;
    var high = hsl.l;
    var best = hslToHex(hsl.h, sat, low);
    for (var i = 0; i < 16; i++) {
      var mid = (low + high) / 2;
      var candidate = hslToHex(hsl.h, sat, mid);
      if (getContrastRatio(candidate, '#FFFFFF') >= target) {
        best = candidate;
        low = mid;
      } else {
        high = mid;
      }
    }
    return best;
  }

  function rgba(hex, alpha) {
    var rgb = hexToRgb(hex);
    return 'rgba(' + rgb.r + ', ' + rgb.g + ', ' + rgb.b + ', ' + alpha + ')';
  }

  function getEventStyles(hex) {
    var primary = normalizeHex(hex);
    // 浅色类别的边框适当压深，保证跨天边框在白/浅灰格底上清晰可见
    var borderColor = getReadableTextColor(primary, 2.2, 3.2);
    var textColor = getReadableTextColor(primary, 3.0, 4.5);
    return {
      primary: primary,
      borderColor: borderColor,
      textColor: textColor,
      softBg: rgba(primary, 0.11),
      chipBg: rgba(primary, 0.12),
      focusRing: rgba(primary, 0.22),
      yearFill: getContrastRatio(primary, '#FFFFFF') >= 2.2 ? primary : borderColor
    };
  }

  WorkCal.Color = {
    PRESET_COLORS: PRESET_COLORS,
    normalizeHex: normalizeHex,
    hexToRgb: hexToRgb,
    rgbToHsl: rgbToHsl,
    hslToHex: hslToHex,
    getRelativeLuminance: getRelativeLuminance,
    getContrastRatio: getContrastRatio,
    getReadableTextColor: getReadableTextColor,
    rgba: rgba,
    getEventStyles: getEventStyles
  };

  if (typeof module !== 'undefined' && module.exports) {
    module.exports = WorkCal.Color;
  }
})(typeof window !== 'undefined' ? window : globalThis);
