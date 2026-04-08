(function() {
  var NODE_COLORS = {
    CAMEL_ROUTE: '#4A90E2',
    CAMEL_ENDPOINT: '#F4AF23',
    CAMEL_PROCESSOR: '#E94B3C',
    CAMEL_BEAN: '#7ED321',
    CAMEL_COMPONENT: '#9013FE',
    CAMEL_DATAFORMAT: '#50E3C2',
    EXTERNAL_SYSTEM: '#8B572A',
    PROPERTY_SOURCE: '#BD10E0'
  };

  var width = window.innerWidth;
  var height = window.innerHeight;

  var svg = d3.select('#graph-container')
    .append('svg')
    .attr('width', width)
    .attr('height', height);

  var g = svg.append('g');

  var zoom = d3.zoom()
    .scaleExtent([0.1, 4])
    .on('zoom', function(event) {
      g.attr('transform', event.transform);
    });
  svg.call(zoom);

  var nodes = graphData.nodes.map(function(n) {
    return {
      id: n.id,
      type: n.type,
      label: n.properties.name || n.id,
      properties: n.properties
    };
  });

  var links = graphData.edges.map(function(e) {
    return {
      source: e.source,
      target: e.target,
      type: e.type
    };
  });

  var simulation = d3.forceSimulation(nodes)
    .force('link', d3.forceLink(links).id(function(d) { return d.id; }).distance(120))
    .force('charge', d3.forceManyBody().strength(-800))
    .force('center', d3.forceCenter(width / 2, height / 2));

  var link = g.append('g')
    .selectAll('line')
    .data(links)
    .enter().append('line')
    .style('stroke', '#555')
    .style('stroke-width', 2);

  var node = g.append('g')
    .selectAll('g')
    .data(nodes)
    .enter().append('g')
    .call(d3.drag()
      .on('start', dragstarted)
      .on('drag', dragged)
      .on('end', dragended));

  node.append('circle')
    .attr('r', 20)
    .style('fill', function(d) { return NODE_COLORS[d.type] || '#999'; })
    .style('stroke', '#fff')
    .style('stroke-width', 2);

  node.append('text')
    .attr('dy', 30)
    .attr('text-anchor', 'middle')
    .style('fill', '#e0e0e0')
    .style('font-size', '10px')
    .text(function(d) { return d.label; });

  simulation.on('tick', function() {
    link
      .attr('x1', function(d) { return d.source.x; })
      .attr('y1', function(d) { return d.source.y; })
      .attr('x2', function(d) { return d.target.x; })
      .attr('y2', function(d) { return d.target.y; });

    node.attr('transform', function(d) { return 'translate(' + d.x + ',' + d.y + ')'; });
  });

  function dragstarted(event, d) {
    if (!event.active) simulation.alphaTarget(0.3).restart();
    d.fx = d.x;
    d.fy = d.y;
  }

  function dragged(event, d) {
    d.fx = event.x;
    d.fy = event.y;
  }

  function dragended(event, d) {
    if (!event.active) simulation.alphaTarget(0);
    d.fx = null;
    d.fy = null;
  }

  node.on('click', function(event, d) {
    var info = document.getElementById('info');
    info.style.display = 'block';
    info.textContent = '';

    var h4 = document.createElement('h4');
    h4.textContent = d.label;
    info.appendChild(h4);

    var typeProp = document.createElement('div');
    typeProp.className = 'prop';
    typeProp.textContent = 'Type: ' + d.type;
    info.appendChild(typeProp);

    Object.keys(d.properties).forEach(function(key) {
      var propDiv = document.createElement('div');
      propDiv.className = 'prop';
      propDiv.textContent = key + ': ' + d.properties[key];
      info.appendChild(propDiv);
    });
  });

  var filtersDiv = document.getElementById('filters');
  var nodeTypes = {};
  graphData.nodes.forEach(function(n) {
    nodeTypes[n.type] = (nodeTypes[n.type] || 0) + 1;
  });

  Object.keys(nodeTypes).sort().forEach(function(type) {
    var div = document.createElement('div');
    div.className = 'filter-group';

    var label = document.createElement('label');
    var checkbox = document.createElement('input');
    checkbox.type = 'checkbox';
    checkbox.checked = true;
    checkbox.dataset.type = type;

    var dot = document.createElement('span');
    dot.className = 'color-dot';
    dot.style.background = NODE_COLORS[type] || '#999';

    var text = document.createTextNode(' ' + type + ' (' + nodeTypes[type] + ')');

    label.appendChild(checkbox);
    label.appendChild(dot);
    label.appendChild(text);
    div.appendChild(label);
    filtersDiv.appendChild(div);
  });

  var statsLine = document.getElementById('stats-line');
  var statsText = document.createTextNode(graphData.nodes.length + ' nodes, ' + graphData.edges.length + ' edges');
  statsLine.appendChild(statsText);

  document.getElementById('search').addEventListener('input', function(e) {
    var query = e.target.value.toLowerCase();
    node.style('opacity', function(d) {
      return !query || d.label.toLowerCase().indexOf(query) >= 0 ? 1 : 0.1;
    });
  });

  filtersDiv.addEventListener('change', function(e) {
    if (e.target.type === 'checkbox') {
      var type = e.target.dataset.type;
      var visible = e.target.checked;
      node.style('opacity', function(d) {
        return d.type === type ? (visible ? 1 : 0.1) : null;
      });
    }
  });
})();
