package com.ammar.wallflow.sites

/** JavaScript injected into the learning browser. */
object SiteScripts {
    /**
     * While a mode is set (window.__wfMode), a tap is reported to the app instead of
     * opening the link. Reports the tapped element, its link, and which attribute holds
     * its image address. Selectors are built from the page, so they work for any site.
     */
    val PICKER = """
(function () {
  if (window.__wfInstalled) return;
  window.__wfInstalled = true;
  function esc(s) { return s.replace(/[^a-zA-Z0-9_\-]/g, function (c) { return '\\' + c; }); }
  function classSel(el) {
    var cls = Array.prototype.slice.call(el.classList || [])
      .filter(function (c) { return c.length < 40; }).slice(0, 3);
    return cls.length ? el.tagName.toLowerCase() + '.' + cls.map(esc).join('.') : null;
  }
  function pathSel(el) {
    if (el.id && !/^[0-9]/.test(el.id)) return '#' + esc(el.id);
    var parts = [], n = el;
    while (n && n.nodeType === 1 && n !== document.body && n !== document.documentElement && parts.length < 8) {
      var idx = 1, s = n;
      while ((s = s.previousElementSibling)) { if (s.tagName === n.tagName) idx++; }
      parts.unshift(n.tagName.toLowerCase() + ':nth-of-type(' + idx + ')');
      n = n.parentElement;
    }
    return parts.join(' > ');
  }
  function info(el) { return { selector: pathSel(el), classSelector: classSel(el) }; }
  document.addEventListener('click', function (e) {
    var mode = window.__wfMode;
    if (!mode) return;
    e.preventDefault();
    e.stopPropagation();
    var el = e.target;
    var a = el.closest ? el.closest('a') : null;
    var img = el.tagName === 'IMG' ? el : (el.querySelector ? el.querySelector('img') : null);
    var attr = (img && img.hasAttribute('data-src')) ? 'data-src' : 'src';
    window.WallFlowPick.report(JSON.stringify({
      mode: mode,
      attr: attr,
      text: (el.textContent || '').trim().slice(0, 60),
      target: info(el),
      link: a ? info(a) : null
    }));
  }, true);
})();
""".trimIndent()

    /** Turns the mode into a JavaScript value. */
    fun modeScript(mode: String?): String =
        "window.__wfMode = " + (if (mode == null) "null" else "\"$mode\"") + ";"
}
