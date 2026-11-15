// Apply the workspace theme to canvas labels as well as HTML surfaces.
(function () {
    function applyChartTheme() {
        if (typeof Chart === 'undefined') return;
        const style = getComputedStyle(document.body);
        const ink = style.getPropertyValue('--ws-ink').trim();
        const line = style.getPropertyValue('--ws-line').trim();
        Object.values(Chart.instances).forEach(chart => {
            chart.options.color = ink;
            if (chart.options.plugins.legend) chart.options.plugins.legend.labels.color = ink;
            Object.values(chart.options.scales || {}).forEach(scale => {
                if (scale.ticks) scale.ticks.color = ink;
                if (scale.grid) scale.grid.color = line;
            });
            chart.update('none');
        });
    }
    applyChartTheme();
    new MutationObserver(applyChartTheme).observe(document.documentElement,
        {attributes: true, attributeFilter: ['data-theme']});
})();
