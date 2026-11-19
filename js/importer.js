/**
 * 零依赖 Excel (.xlsx) / CSV / 复制粘贴 台账导入解析引擎
 * 满足 FR-10 / BR-2 / BR-3 / AC-13 / AC-14 / AC-15 / NFR-1
 *
 * - 纯 JS ZIP Central Directory 读取 + DecompressionStream 优先 + 纯 JS RFC1951 Inflate 兜底
 * - 纯 JS 轻量 XML 解析（浏览器与 Node 行为完全一致）
 * - 支持合并单元格（<mergeCells>）与空延续行继承
 * - 支持自动定位表头行与列名关键词匹配
 * - 支持“预评估”“正式评估”拆分为独立日程，阶段可映射至任意类别
 */
(function (root) {
  'use strict';

  var WorkCal = (root.WorkCal = root.WorkCal || {});

  // =========================================================================
  // 1. 纯 JS RFC 1951 Raw DEFLATE 解压器（DecompressionStream 不可用时的兜底）
  // =========================================================================
  var LENGTH_BASE = [
    3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31,
    35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258
  ];
  var LENGTH_EXTRA = [
    0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2,
    3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0
  ];
  var DIST_BASE = [
    1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193,
    257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577
  ];
  var DIST_EXTRA = [
    0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6,
    7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13
  ];
  var CL_ORDER = [16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15];

  function buildHuffmanTable(lengths) {
    var maxBits = 0;
    var i;
    for (i = 0; i < lengths.length; i++) {
      if (lengths[i] > maxBits) maxBits = lengths[i];
    }
    if (maxBits === 0) return { maxBits: 1, table: [0] };

    var counts = new Int32Array(maxBits + 1);
    for (i = 0; i < lengths.length; i++) {
      if (lengths[i] > 0) counts[lengths[i]]++;
    }

    var offsets = new Int32Array(maxBits + 1);
    var code = 0;
    for (var b = 1; b <= maxBits; b++) {
      code = (code + counts[b - 1]) << 1;
      offsets[b] = code;
    }

    var size = 1 << maxBits;
    var table = new Int32Array(size);
    table.fill(-1);

    for (var sym = 0; sym < lengths.length; sym++) {
      var len = lengths[sym];
      if (len === 0) continue;
      var c = offsets[len]++;
      // 反转比特以便 LSB-first 直接查表
      var rev = 0;
      for (var k = 0; k < len; k++) {
        rev = (rev << 1) | ((c >>> k) & 1);
      }
      var step = 1 << len;
      var val = (sym << 5) | len;
      for (var slot = rev; slot < size; slot += step) {
        table[slot] = val;
      }
    }
    return { maxBits: maxBits, table: table };
  }

  var FIXED_LIT_TABLE = (function () {
    var lens = new Uint8Array(288);
    var i;
    for (i = 0; i <= 143; i++) lens[i] = 8;
    for (i = 144; i <= 255; i++) lens[i] = 9;
    for (i = 256; i <= 279; i++) lens[i] = 7;
    for (i = 280; i <= 287; i++) lens[i] = 8;
    return buildHuffmanTable(lens);
  })();

  var FIXED_DIST_TABLE = (function () {
    var lens = new Uint8Array(32);
    lens.fill(5);
    return buildHuffmanTable(lens);
  })();

  function inflateRaw(input) {
    var pos = 0;
    var bitBuf = 0;
    var bitCnt = 0;
    var out = new Uint8Array(Math.max(256, input.length * 3));
    var outLen = 0;

    function ensure(n) {
      if (outLen + n <= out.length) return;
      var next = new Uint8Array(Math.max(out.length * 2, outLen + n + 1024));
      next.set(out.subarray(0, outLen));
      out = next;
    }

    function fillBits(n) {
      while (bitCnt < n) {
        var b = pos < input.length ? input[pos++] : 0;
        bitBuf |= b << bitCnt;
        bitCnt += 8;
      }
    }

    function readBits(n) {
      if (n === 0) return 0;
      fillBits(n);
      var v = bitBuf & ((1 << n) - 1);
      bitBuf >>>= n;
      bitCnt -= n;
      return v;
    }

    function decodeSym(ht) {
      fillBits(ht.maxBits);
      var entry = ht.table[bitBuf & ((1 << ht.maxBits) - 1)];
      if (entry < 0) throw new Error('DEFLATE 码流损坏');
      var len = entry & 31;
      bitBuf >>>= len;
      bitCnt -= len;
      return entry >>> 5;
    }

    var bfinal = 0;
    do {
      bfinal = readBits(1);
      var btype = readBits(2);
      if (btype === 0) {
        bitBuf = 0;
        bitCnt = 0;
        var len = input[pos] | (input[pos + 1] << 8);
        pos += 4; // 跳过 len 与 nlen
        ensure(len);
        out.set(input.subarray(pos, pos + len), outLen);
        outLen += len;
        pos += len;
      } else if (btype === 1 || btype === 2) {
        var litTable = FIXED_LIT_TABLE;
        var distTable = FIXED_DIST_TABLE;

        if (btype === 2) {
          var hlit = readBits(5) + 257;
          var hdist = readBits(5) + 1;
          var hclen = readBits(4) + 4;
          var clLens = new Uint8Array(19);
          for (var i = 0; i < hclen; i++) {
            clLens[CL_ORDER[i]] = readBits(3);
          }
          var clTable = buildHuffmanTable(clLens);
          var total = hlit + hdist;
          var allLens = new Uint8Array(total);
          var idx = 0;
          while (idx < total) {
            var sym = decodeSym(clTable);
            if (sym < 16) {
              allLens[idx++] = sym;
            } else {
              var rep = 0;
              var val = 0;
              if (sym === 16) {
                val = idx > 0 ? allLens[idx - 1] : 0;
                rep = 3 + readBits(2);
              } else if (sym === 17) {
                rep = 3 + readBits(3);
              } else {
                rep = 11 + readBits(7);
              }
              while (rep-- > 0 && idx < total) {
                allLens[idx++] = val;
              }
            }
          }
          litTable = buildHuffmanTable(allLens.subarray(0, hlit));
          distTable = buildHuffmanTable(allLens.subarray(hlit, hlit + hdist));
        }

        while (true) {
          var s = decodeSym(litTable);
          if (s < 256) {
            ensure(1);
            out[outLen++] = s;
          } else if (s === 256) {
            break;
          } else {
            var lIdx = s - 257;
            var matchLen = LENGTH_BASE[lIdx] + readBits(LENGTH_EXTRA[lIdx]);
            var dSym = decodeSym(distTable);
            var dist = DIST_BASE[dSym] + readBits(DIST_EXTRA[dSym]);
            ensure(matchLen);
            for (var m = 0; m < matchLen; m++) {
              out[outLen] = out[outLen - dist];
              outLen++;
            }
          }
        }
      } else {
        throw new Error('不支持的 DEFLATE 块类型');
      }
    } while (!bfinal);

    return out.subarray(0, outLen);
  }

  function decompressRawAsync(bytes, method) {
    if (method === 0) return Promise.resolve(bytes);
    if (method !== 8) {
      return Promise.reject(new Error('不支持的 ZIP 压缩方式: ' + method));
    }
    if (typeof DecompressionStream !== 'undefined') {
      try {
        var ds = new DecompressionStream('deflate-raw');
        var writer = ds.writable.getWriter();
        writer.write(bytes);
        writer.close();
        return new Response(ds.readable)
          .arrayBuffer()
          .then(function (ab) {
            return new Uint8Array(ab);
          })
          .catch(function () {
            return inflateRaw(bytes);
          });
      } catch (e) {
        return Promise.resolve(inflateRaw(bytes));
      }
    }
    return Promise.resolve(inflateRaw(bytes));
  }

  // =========================================================================
  // 2. 纯 JS ZIP 读取器（EOCD + Central Directory）
  // =========================================================================
  function readU16(bytes, off) {
    return bytes[off] | (bytes[off + 1] << 8);
  }

  function readU32(bytes, off) {
    return (
      (bytes[off] |
        (bytes[off + 1] << 8) |
        (bytes[off + 2] << 16) |
        (bytes[off + 3] << 24)) >>>
      0
    );
  }

  function readZipDirectory(bytes) {
    // 从末尾逆向定位 EOCD (0x06054b50)
    var minPos = Math.max(0, bytes.length - 65557);
    var eocdOffset = -1;
    for (var p = bytes.length - 22; p >= minPos; p--) {
      if (
        bytes[p] === 0x50 &&
        bytes[p + 1] === 0x4b &&
        bytes[p + 2] === 0x05 &&
        bytes[p + 3] === 0x06
      ) {
        var commentLen = readU16(bytes, p + 20);
        if (p + 22 + commentLen === bytes.length) {
          eocdOffset = p;
          break;
        }
      }
    }
    if (eocdOffset < 0) {
      throw new Error('文件不是有效的 .xlsx（ZIP 结构缺失）');
    }

    var totalEntries = readU16(bytes, eocdOffset + 10);
    var cdOffset = readU32(bytes, eocdOffset + 16);
    var decoder = new TextDecoder('utf-8');
    var entries = {};
    var ptr = cdOffset;

    for (var i = 0; i < totalEntries; i++) {
      if (readU32(bytes, ptr) !== 0x02014b50) break;
      var method = readU16(bytes, ptr + 10);
      var compSize = readU32(bytes, ptr + 20);
      var uncompSize = readU32(bytes, ptr + 24);
      var fnameLen = readU16(bytes, ptr + 28);
      var extraLen = readU16(bytes, ptr + 30);
      var commentLen2 = readU16(bytes, ptr + 32);
      var localHeaderOffset = readU32(bytes, ptr + 42);
      var name = decoder.decode(bytes.subarray(ptr + 46, ptr + 46 + fnameLen));

      entries[name] = {
        name: name,
        method: method,
        compSize: compSize,
        uncompSize: uncompSize,
        localHeaderOffset: localHeaderOffset
      };
      ptr += 46 + fnameLen + extraLen + commentLen2;
    }

    return entries;
  }

  function extractZipTextAsync(bytes, entry) {
    if (!entry) return Promise.resolve(null);
    var lh = entry.localHeaderOffset;
    if (readU32(bytes, lh) !== 0x04034b50) {
      return Promise.reject(new Error('ZIP 本地文件头损坏: ' + entry.name));
    }
    var fnameLen = readU16(bytes, lh + 26);
    var extraLen = readU16(bytes, lh + 28);
    var dataStart = lh + 30 + fnameLen + extraLen;
    var slice = bytes.subarray(dataStart, dataStart + entry.compSize);
    return decompressRawAsync(slice, entry.method).then(function (raw) {
      return new TextDecoder('utf-8').decode(raw);
    });
  }

  // =========================================================================
  // 3. 轻量级纯 JS XML 解析器（无 DOMParser 跨端差异）
  // =========================================================================
  function decodeXmlEntities(str) {
    if (!str || str.indexOf('&') === -1) return str;
    return str.replace(/&(#x?[0-9a-fA-F]+|lt|gt|amp|quot|apos);/g, function (_, ent) {
      switch (ent) {
        case 'lt': return '<';
        case 'gt': return '>';
        case 'amp': return '&';
        case 'quot': return '"';
        case 'apos': return "'";
        default:
          if (ent[0] === '#') {
            var code =
              ent[1] === 'x' || ent[1] === 'X'
                ? parseInt(ent.slice(2), 16)
                : parseInt(ent.slice(1), 10);
            return isFinite(code) ? String.fromCodePoint(code) : '';
          }
          return _;
      }
    });
  }

  function parseXmlAttrs(attrStr) {
    var attrs = {};
    if (!attrStr) return attrs;
    var re = /([A-Za-z0-9_:\-]+)\s*=\s*(?:"([^"]*)"|'([^']*)')/g;
    var m;
    while ((m = re.exec(attrStr)) !== null) {
      attrs[m[1]] = decodeXmlEntities(m[2] !== undefined ? m[2] : m[3]);
    }
    return attrs;
  }

  function parseXmlTree(xmlText) {
    var rootNode = { tag: '#root', attrs: {}, children: [], text: '' };
    var stack = [rootNode];
    var tagRe = /<(\/?)([A-Za-z0-9_:\-]+)([^>]*?)(\/?)>/g;
    var lastIdx = 0;
    var m;

    // 剥离 XML 声明与注释
    var clean = xmlText.replace(/<\?[\s\S]*?\?>/g, '').replace(/<!--[\s\S]*?-->/g, '');

    while ((m = tagRe.exec(clean)) !== null) {
      if (m.index > lastIdx) {
        var txt = decodeXmlEntities(clean.slice(lastIdx, m.index));
        stack[stack.length - 1].text += txt;
      }
      lastIdx = tagRe.lastIndex;

      var isClose = m[1] === '/';
      var fullName = m[2];
      var shortName = fullName.indexOf(':') >= 0 ? fullName.split(':').pop() : fullName;
      var attrRaw = m[3];
      var selfClose = m[4] === '/';

      if (isClose) {
        if (stack.length > 1) stack.pop();
      } else {
        var node = {
          tag: shortName,
          fullTag: fullName,
          attrs: parseXmlAttrs(attrRaw),
          children: [],
          text: ''
        };
        stack[stack.length - 1].children.push(node);
        if (!selfClose) {
          stack.push(node);
        }
      }
    }
    return rootNode;
  }

  function findChildren(node, tagName) {
    var res = [];
    for (var i = 0; i < node.children.length; i++) {
      if (node.children[i].tag === tagName) res.push(node.children[i]);
    }
    return res;
  }

  function findFirst(node, tagName) {
    for (var i = 0; i < node.children.length; i++) {
      if (node.children[i].tag === tagName) return node.children[i];
      var deep = findFirst(node.children[i], tagName);
      if (deep) return deep;
    }
    return null;
  }

  function collectText(node) {
    var s = node.text || '';
    for (var i = 0; i < node.children.length; i++) {
      s += collectText(node.children[i]);
    }
    return s;
  }

  // =========================================================================
  // 4. .xlsx 解析器（含合并单元格、日期样式识别、sharedStrings）
  // =========================================================================
  function colLettersToIndex(letters) {
    var idx = 0;
    for (var i = 0; i < letters.length; i++) {
      idx = idx * 26 + (letters.charCodeAt(i) - 64);
    }
    return idx - 1;
  }

  function parseCellRef(ref) {
    var m = /^([A-Z]+)(\d+)$/i.exec(String(ref || '').trim());
    if (!m) return null;
    return {
      col: colLettersToIndex(m[1].toUpperCase()),
      row: parseInt(m[2], 10) - 1
    };
  }

  function isDateFormatCode(code) {
    if (!code) return false;
    var clean = code.replace(/"[^"]*"/g, '').replace(/\[[^\]]*\]/g, '');
    return /[ymd年]/i.test(clean) && !/^[@0#.,%?\s]+$/.test(clean);
  }

  function parseDateStyleSet(stylesXml) {
    var dateStyleIndexes = {};
    if (!stylesXml) return dateStyleIndexes;

    var tree = parseXmlTree(stylesXml);
    var customFormats = {};
    var numFmtsNode = findFirst(tree, 'numFmts');
    if (numFmtsNode) {
      findChildren(numFmtsNode, 'numFmt').forEach(function (nf) {
        if (nf.attrs.numFmtId && nf.attrs.formatCode) {
          customFormats[nf.attrs.numFmtId] = nf.attrs.formatCode;
        }
      });
    }

    var BUILTIN_DATE_IDS = {
      14: 1, 15: 1, 16: 1, 17: 1, 18: 1, 19: 1, 20: 1, 21: 1, 22: 1,
      27: 1, 28: 1, 29: 1, 30: 1, 31: 1, 36: 1, 45: 1, 46: 1, 47: 1, 50: 1, 57: 1
    };

    var cellXfs = findFirst(tree, 'cellXfs');
    if (cellXfs) {
      var xfs = findChildren(cellXfs, 'xf');
      for (var i = 0; i < xfs.length; i++) {
        var fmtId = xfs[i].attrs.numFmtId;
        if (!fmtId) continue;
        var nId = parseInt(fmtId, 10);
        if (BUILTIN_DATE_IDS[nId] || isDateFormatCode(customFormats[fmtId])) {
          dateStyleIndexes[String(i)] = true;
        }
      }
    }
    return dateStyleIndexes;
  }

  function parseSharedStrings(sstXml) {
    if (!sstXml) return [];
    var tree = parseXmlTree(sstXml);
    var sst = findFirst(tree, 'sst') || tree;
    var items = findChildren(sst, 'si');
    var list = new Array(items.length);
    for (var i = 0; i < items.length; i++) {
      var si = items[i];
      var runs = findChildren(si, 'r');
      if (runs.length) {
        var buf = '';
        for (var r = 0; r < runs.length; r++) {
          var tNode = findFirst(runs[r], 't');
          if (tNode) buf += collectText(tNode);
        }
        list[i] = buf;
      } else {
        var tOnly = findFirst(si, 't');
        list[i] = tOnly ? collectText(tOnly) : collectText(si);
      }
    }
    return list;
  }

  function resolveFirstSheetPath(workbookXml, relsXml) {
    var defaultPath = 'xl/worksheets/sheet1.xml';
    if (!workbookXml) return defaultPath;

    var wbTree = parseXmlTree(workbookXml);
    var workbookPr = findFirst(wbTree, 'workbookPr');
    var date1904 = workbookPr && workbookPr.attrs.date1904 === '1';

    var sheetsNode = findFirst(wbTree, 'sheets');
    var firstSheet = sheetsNode ? findChildren(sheetsNode, 'sheet')[0] : null;
    if (!firstSheet) return { path: defaultPath, date1904: date1904 };

    var rid = firstSheet.attrs['r:id'] || firstSheet.attrs.id;
    if (rid && relsXml) {
      var relTree = parseXmlTree(relsXml);
      var relsRoot = findFirst(relTree, 'Relationships') || relTree;
      var rels = findChildren(relsRoot, 'Relationship');
      for (var i = 0; i < rels.length; i++) {
        if (rels[i].attrs.Id === rid && rels[i].attrs.Target) {
          var target = rels[i].attrs.Target.replace(/^\/+/, '');
          if (target.indexOf('xl/') !== 0) target = 'xl/' + target;
          return { path: target, date1904: date1904 };
        }
      }
    }
    return { path: defaultPath, date1904: date1904 };
  }

  function parseXlsxAsync(arrayBuffer) {
    var bytes = new Uint8Array(arrayBuffer);
    // 检测旧版 .xls OLE2 复合文档魔数 D0 CF 11 E0
    if (
      bytes.length >= 4 &&
      bytes[0] === 0xd0 &&
      bytes[1] === 0xcf &&
      bytes[2] === 0x11 &&
      bytes[3] === 0xe0
    ) {
      return Promise.reject(
        new Error('不支持旧版 .xls 二进制格式，请在 Excel 中另存为 .xlsx/.csv，或直接复制表格内容粘贴导入')
      );
    }

    var entries;
    try {
      entries = readZipDirectory(bytes);
    } catch (e) {
      return Promise.reject(e);
    }

    return Promise.all([
      extractZipTextAsync(bytes, entries['xl/workbook.xml']),
      extractZipTextAsync(bytes, entries['xl/_rels/workbook.xml.rels']),
      extractZipTextAsync(bytes, entries['xl/sharedStrings.xml']),
      extractZipTextAsync(bytes, entries['xl/styles.xml'])
    ]).then(function (parts) {
      var sheetInfo = resolveFirstSheetPath(parts[0], parts[1]);
      var sheetEntry = entries[sheetInfo.path] || entries['xl/worksheets/sheet1.xml'];
      if (!sheetEntry) {
        throw new Error('未在 .xlsx 文件中找到工作表数据');
      }
      var sst = parseSharedStrings(parts[2]);
      var dateStyles = parseDateStyleSet(parts[3]);

      return extractZipTextAsync(bytes, sheetEntry).then(function (sheetXml) {
        var DU = WorkCal.DateUtils;
        var tree = parseXmlTree(sheetXml);
        var ws = findFirst(tree, 'worksheet') || tree;
        var sheetData = findFirst(ws, 'sheetData');
        var grid = [];
        var maxCol = 0;

        if (sheetData) {
          var rows = findChildren(sheetData, 'row');
          for (var r = 0; r < rows.length; r++) {
            var rowNode = rows[r];
            var rowNumHint = rowNode.attrs.r ? parseInt(rowNode.attrs.r, 10) - 1 : r;
            var cells = findChildren(rowNode, 'c');
            var fallbackCol = 0;

            for (var c = 0; c < cells.length; c++) {
              var cell = cells[c];
              var pos = cell.attrs.r ? parseCellRef(cell.attrs.r) : null;
              var rowIdx = pos ? pos.row : rowNumHint;
              var colIdx = pos ? pos.col : fallbackCol;
              fallbackCol = colIdx + 1;

              while (grid.length <= rowIdx) grid.push([]);
              if (colIdx + 1 > maxCol) maxCol = colIdx + 1;

              var cellType = cell.attrs.t || '';
              var styleIdx = cell.attrs.s || '';
              var vNode = findFirst(cell, 'v');
              var isNode = findFirst(cell, 'is');
              var val = '';

              if (cellType === 's') {
                var sIdx = vNode ? parseInt(collectText(vNode), 10) : -1;
                val = sIdx >= 0 && sIdx < sst.length ? sst[sIdx] : '';
              } else if (cellType === 'inlineStr') {
                val = isNode ? collectText(isNode) : '';
              } else if (cellType === 'str' || cellType === 'e') {
                val = vNode ? collectText(vNode) : '';
              } else if (cellType === 'b') {
                val = vNode && collectText(vNode) === '1' ? 'TRUE' : 'FALSE';
              } else if (vNode) {
                var rawNumStr = collectText(vNode).trim();
                if (rawNumStr !== '') {
                  var num = Number(rawNumStr);
                  // 若单元格是日期样式或 5 位有效 Excel 日期序列号且该列在常规日期范围，按日期转出
                  if (
                    dateStyles[styleIdx] &&
                    isFinite(num) &&
                    num >= 1 &&
                    num <= 100000
                  ) {
                    val =
                      DU.parseSingleDate(num, { date1904: sheetInfo.date1904 }) ||
                      rawNumStr;
                  } else {
                    val = rawNumStr;
                  }
                }
              }

              grid[rowIdx][colIdx] = String(val || '').trim();
            }
          }
        }

        // 填充合并单元格区域（<mergeCells>）：区域内所有格继承左上角主值（FR-10 / AC-13）
        var mergeCellsNode = findFirst(ws, 'mergeCells');
        if (mergeCellsNode) {
          var merges = findChildren(mergeCellsNode, 'mergeCell');
          for (var m = 0; m < merges.length; m++) {
            var refStr = merges[m].attrs.ref;
            if (!refStr || refStr.indexOf(':') === -1) continue;
            var segs = refStr.split(':');
            var tl = parseCellRef(segs[0]);
            var br = parseCellRef(segs[1]);
            if (!tl || !br) continue;
            var masterVal =
              grid[tl.row] && grid[tl.row][tl.col] !== undefined
                ? grid[tl.row][tl.col]
                : '';
            if (!masterVal) continue;
            for (var mr = tl.row; mr <= br.row; mr++) {
              while (grid.length <= mr) grid.push([]);
              for (var mc = tl.col; mc <= br.col; mc++) {
                if (mc + 1 > maxCol) maxCol = mc + 1;
                if (!grid[mr][mc]) {
                  grid[mr][mc] = masterVal;
                }
              }
            }
          }
        }

        // 补齐每行列宽
        for (var rr = 0; rr < grid.length; rr++) {
          if (!grid[rr]) grid[rr] = [];
          for (var cc = 0; cc < maxCol; cc++) {
            if (grid[rr][cc] === undefined) grid[rr][cc] = '';
          }
        }

        return { rows: grid, date1904: sheetInfo.date1904 };
      });
    });
  }

  // =========================================================================
  // 5. CSV / Excel 复制粘贴 TSV 解析器
  // =========================================================================
  function decodeCsvBytes(arrayBuffer) {
    var bytes = new Uint8Array(arrayBuffer);
    // UTF-8 BOM
    if (bytes.length >= 3 && bytes[0] === 0xef && bytes[1] === 0xbb && bytes[2] === 0xbf) {
      return new TextDecoder('utf-8').decode(bytes.subarray(3));
    }
    try {
      return new TextDecoder('utf-8', { fatal: true }).decode(bytes);
    } catch (e) {
      // 中文 Excel 导出的 ANSI CSV 常见为 GBK/GB18030
      try {
        return new TextDecoder('gb18030').decode(bytes);
      } catch (e2) {
        return new TextDecoder('utf-8').decode(bytes);
      }
    }
  }

  function parseDelimitedText(text, forceDelimiter) {
    var clean = String(text || '').replace(/^\uFEFF/, '');
    if (!clean.trim()) return [];

    var delimiter = forceDelimiter;
    if (!delimiter) {
      var firstLine = clean.split(/\r?\n/)[0] || '';
      var tabCount = (firstLine.match(/\t/g) || []).length;
      var commaCount = (firstLine.match(/,/g) || []).length;
      delimiter = tabCount >= commaCount && tabCount > 0 ? '\t' : ',';
    }

    var rows = [];
    var row = [];
    var field = '';
    var inQuotes = false;

    for (var i = 0; i < clean.length; i++) {
      var ch = clean[i];
      if (inQuotes) {
        if (ch === '"') {
          if (clean[i + 1] === '"') {
            field += '"';
            i++;
          } else {
            inQuotes = false;
          }
        } else {
          field += ch;
        }
      } else {
        if (ch === '"') {
          inQuotes = true;
        } else if (ch === delimiter) {
          row.push(field.trim());
          field = '';
        } else if (ch === '\r' || ch === '\n') {
          if (ch === '\r' && clean[i + 1] === '\n') i++;
          row.push(field.trim());
          rows.push(row);
          row = [];
          field = '';
        } else {
          field += ch;
        }
      }
    }
    if (field !== '' || row.length > 0) {
      row.push(field.trim());
      rows.push(row);
    }
    return rows;
  }

  // =========================================================================
  // 6. 《评估项目台账》表头识别、业务转换与去重预览（FR-10 / BR-2）
  // =========================================================================
  var HEADER_RULES = [
    { key: 'index', keywords: ['序号', '编号', 'no'] },
    { key: 'projectName', keywords: ['项目名称', '评估项目', '项目名', '项目'] },
    { key: 'level', keywords: ['项目等级', '等级', '级别'] },
    { key: 'role', keywords: ['承担角色', '角色', '分工', '职责'] },
    { key: 'stage', keywords: ['评估阶段', '评估类型', '评估节点', '阶段'] },
    { key: 'startDate', keywords: ['开始时间', '开始日期', '起始时间', '起始日期', '起止时间', '时间', '日期'] },
    { key: 'endDate', keywords: ['结束时间', '结束日期', '截止时间', '截止日期', '终止时间', '完成时间'] },
    { key: 'notes', keywords: ['备注', '说明', '地点', '联系人'] }
  ];

  // 按关键词长度降序拍平，防止短泛化词（如“时间”“项目”）抢先截胡长专有词（如“结束时间”“项目开始时间”）
  var HEADER_SUBSTRING_LIST = (function () {
    var list = [];
    for (var i = 0; i < HEADER_RULES.length; i++) {
      var rule = HEADER_RULES[i];
      for (var k = 0; k < rule.keywords.length; k++) {
        list.push({ key: rule.key, kw: rule.keywords[k] });
      }
    }
    list.sort(function (a, b) {
      return b.kw.length - a.kw.length;
    });
    return list;
  })();

  function matchHeaderCell(text) {
    var norm = String(text || '')
      .replace(/[\s：:（）()_\-]/g, '')
      .toLowerCase();
    if (!norm) return null;
    var i;
    // 第1轮：精确匹配
    for (i = 0; i < HEADER_SUBSTRING_LIST.length; i++) {
      if (norm === HEADER_SUBSTRING_LIST[i].kw) {
        return HEADER_SUBSTRING_LIST[i].key;
      }
    }
    // 第2轮：按关键词长度降序子串匹配
    for (i = 0; i < HEADER_SUBSTRING_LIST.length; i++) {
      if (norm.indexOf(HEADER_SUBSTRING_LIST[i].kw) !== -1) {
        return HEADER_SUBSTRING_LIST[i].key;
      }
    }
    return null;
  }

  function detectHeaderRow(rows) {
    var limit = Math.min(rows.length, 25);
    var bestIdx = -1;
    var bestScore = 0;
    var bestCols = null;

    for (var r = 0; r < limit; r++) {
      var row = rows[r] || [];
      var colMap = {};
      var score = 0;
      for (var c = 0; c < row.length; c++) {
        var matched = matchHeaderCell(row[c]);
        if (matched && colMap[matched] === undefined) {
          colMap[matched] = c;
          score += matched === 'projectName' || matched === 'startDate' || matched === 'stage' ? 2 : 1;
        }
      }
      if (colMap.projectName !== undefined && colMap.startDate !== undefined && score > bestScore) {
        bestScore = score;
        bestIdx = r;
        bestCols = colMap;
      }
    }

    if (bestIdx === -1) {
      throw new Error(
        '未能识别表头列，请确认表格包含“项目名称”“开始时间”（及可选的“评估阶段”“结束时间”“等级”“角色”“备注”）'
      );
    }
    return { headerRowIndex: bestIdx, columns: bestCols };
  }

  function defaultCategoryIdForStage(categories) {
    for (var i = 0; i < categories.length; i++) {
      if (categories[i].name === '外出评估') return categories[i].id;
    }
    return categories.length ? categories[0].id : '__fallback__';
  }

  /**
   * 解析二维网格为台账预览结果（不写入存储）
   */
  function parseLedgerGrid(rows, options) {
    var DU = WorkCal.DateUtils;
    var header = detectHeaderRow(rows);
    var cols = header.columns;

    var parsedItems = [];
    var skippedRows = [];
    var stageCounts = {};
    var lastProject = '';
    var lastLevel = '';
    var lastRole = '';

    for (var r = header.headerRowIndex + 1; r < rows.length; r++) {
      var row = rows[r] || [];
      var excelRowNum = r + 1;

      var getVal = function (key) {
        var idx = cols[key];
        if (idx === undefined) return '';
        return String(row[idx] !== undefined ? row[idx] : '').trim();
      };

      var rawIdx = getVal('index');
      var rawProject = getVal('projectName');
      var rawLevel = getVal('level');
      var rawRole = getVal('role');
      var rawStage = getVal('stage');
      var rawStart = getVal('startDate');
      var rawEnd = getVal('endDate');
      var rawNotes = getVal('notes');

      // 整行全空跳过（静默空行）
      if (!rawIdx && !rawProject && !rawStage && !rawStart && !rawEnd && !rawNotes) {
        continue;
      }
      // 常见底部汇总行跳过
      if (/^(合计|总计|小计|备注说明)[:：]?$/.test(rawIdx || rawProject)) {
        continue;
      }

      // 合并单元格或手写省略时，沿用上一行项目名称、等级与角色（FR-10）
      var projectName = rawProject || lastProject;
      if (rawProject) {
        lastProject = rawProject;
        lastLevel = rawLevel;
        lastRole = rawRole;
      } else {
        if (!rawLevel && lastLevel) rawLevel = lastLevel;
        if (!rawRole && lastRole) rawRole = lastRole;
      }

      if (!projectName) {
        skippedRows.push({
          rowNumber: excelRowNum,
          projectName: '（未填写项目）',
          stage: rawStage,
          reason: '缺少项目名称'
        });
        continue;
      }

      var stage = rawStage || '评估';

      // 若没有单独“结束时间”列且开始时间写的是区间，parseDateRange 会自动拆解
      var effectiveRawEnd = cols.endDate === undefined ? '' : rawEnd;
      var range = DU.parseDateRange(rawStart, effectiveRawEnd, {
        date1904: Boolean(options && options.date1904)
      });

      if (!range.ok) {
        skippedRows.push({
          rowNumber: excelRowNum,
          projectName: projectName,
          stage: stage,
          reason: range.reason
        });
        continue;
      }

      stageCounts[stage] = (stageCounts[stage] || 0) + 1;

      var metaParts = ['阶段：' + stage];
      if (rawLevel) metaParts.push('等级：' + rawLevel);
      if (rawRole) metaParts.push('角色：' + rawRole);
      if (rawNotes) metaParts.push(rawNotes);

      var ledgerKey =
        projectName + '||' + stage + '||' + range.startDate + '||' + range.endDate;

      parsedItems.push({
        rowNumber: excelRowNum,
        projectName: projectName,
        stage: stage,
        level: rawLevel,
        role: rawRole,
        startDate: range.startDate,
        endDate: range.endDate,
        notes: metaParts.join(' | ').slice(0, 500),
        ledgerKey: ledgerKey,
        ledgerMeta: {
          projectName: projectName,
          stage: stage,
          level: rawLevel,
          role: rawRole
        }
      });
    }

    return {
      items: parsedItems,
      skipped: skippedRows,
      stageCounts: stageCounts,
      stages: Object.keys(stageCounts)
    };
  }

  /**
   * 结合当前 store 构建带映射与新增/更新预览状态的数据
   */
  function buildImportPreview(parsedResult, store, customStageMap) {
    var categories = store.getCategories();
    var defaultCatId = defaultCategoryIdForStage(categories);
    var stageMap = {};

    parsedResult.stages.forEach(function (st) {
      if (customStageMap && customStageMap[st]) {
        stageMap[st] = customStageMap[st];
      } else {
        stageMap[st] = defaultCatId;
      }
    });

    // 统计现有台账去重键（手填不参与去重，BR-2）
    var existingLedgerKeys = {};
    var events = store.getEvents();
    for (var i = 0; i < events.length; i++) {
      if (events[i].source === 'ledger' && events[i].ledgerKey) {
        existingLedgerKeys[events[i].ledgerKey] = true;
      }
    }

    var newCount = 0;
    var updateCount = 0;
    var mappedEntries = parsedResult.items.map(function (item) {
      var isUpdate = Boolean(existingLedgerKeys[item.ledgerKey]);
      if (isUpdate) updateCount++;
      else newCount++;

      return {
        rowNumber: item.rowNumber,
        title: item.projectName.slice(0, 60),
        categoryId: stageMap[item.stage] || defaultCatId,
        startDate: item.startDate,
        endDate: item.endDate,
        notes: item.notes,
        ledgerKey: item.ledgerKey,
        ledgerMeta: item.ledgerMeta,
        isUpdate: isUpdate
      };
    });

    return {
      entries: mappedEntries,
      skipped: parsedResult.skipped,
      stageCounts: parsedResult.stageCounts,
      stages: parsedResult.stages,
      stageMap: stageMap,
      summary: {
        totalRecognized: mappedEntries.length,
        newCount: newCount,
        updateCount: updateCount,
        skippedCount: parsedResult.skipped.length
      }
    };
  }

  function parseFileAsync(file) {
    var name = String((file && file.name) || '').toLowerCase();
    if (name.endsWith('.xls')) {
      return Promise.reject(
        new Error('不支持旧版 .xls 文件，请用 Excel 另存为 .xlsx 或 .csv 后导入，或直接复制表格粘贴')
      );
    }
    if (name.endsWith('.csv') || name.endsWith('.tsv') || name.endsWith('.txt')) {
      return file.arrayBuffer().then(function (buf) {
        var text = decodeCsvBytes(buf);
        return { rows: parseDelimitedText(text), date1904: false };
      });
    }
    if (name.endsWith('.xlsx')) {
      return file.arrayBuffer().then(function (buf) {
        return parseXlsxAsync(buf);
      });
    }
    return Promise.reject(new Error('仅支持 .xlsx 或 .csv 文件，也可直接粘贴表格内容'));
  }

  WorkCal.Importer = {
    inflateRaw: inflateRaw,
    parseXlsxAsync: parseXlsxAsync,
    parseDelimitedText: parseDelimitedText,
    parseLedgerGrid: parseLedgerGrid,
    buildImportPreview: buildImportPreview,
    parseFileAsync: parseFileAsync
  };

  if (typeof module !== 'undefined' && module.exports) {
    module.exports = WorkCal.Importer;
  }
})(typeof window !== 'undefined' ? window : globalThis);
