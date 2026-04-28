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

  var elements = [];
  var nodeIds = {};
  graphData.nodes.forEach(function(n) {
    nodeIds[n.id] = true;
    elements.push({
      group: 'nodes',
      data: {
        id: n.id,
        label: n.properties.name || n.id,
        type: n.type,
        properties: n.properties
      }
    });
  });
  graphData.edges.forEach(function(e) {
    if (!nodeIds[e.source] || !nodeIds[e.target]) return;
    elements.push({
      group: 'edges',
      data: {
        source: e.source,
        target: e.target,
        label: e.type
      }
    });
  });

  var cy = cytoscape({
    container: document.getElementById('graph-container'),
    elements: elements,
    style: [
      {
        selector: 'node',
        style: {
          'background-color': function(ele) { return NODE_COLORS[ele.data('type')] || '#999'; },
          'label': 'data(label)',
          'color': '#fff',
          'text-valign': 'center',
          'text-halign': 'center',
          'font-size': '10px',
          'width': 40,
          'height': 40
        }
      },
      {
        selector: 'edge',
        style: {
          'width': 2,
          'line-color': '#555',
          'target-arrow-color': '#555',
          'target-arrow-shape': 'triangle',
          'curve-style': 'bezier',
          'label': 'data(label)',
          'font-size': '8px',
          'color': '#888',
          'text-rotation': 'autorotate'
        }
      }
    ],
    layout: {
      name: 'cose',
      animate: false,
      nodeRepulsion: 8000,
      idealEdgeLength: 100
    }
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

  cy.on('tap', 'node', function(evt) {
    var node = evt.target;
    var info = document.getElementById('info');
    info.style.display = 'block';
    info.textContent = '';

    var h4 = document.createElement('h4');
    h4.textContent = node.data('label');
    info.appendChild(h4);

    var typeProp = document.createElement('div');
    typeProp.className = 'prop';
    typeProp.textContent = 'Type: ' + node.data('type');
    info.appendChild(typeProp);

    var props = node.data('properties');
    Object.keys(props).forEach(function(key) {
      var propDiv = document.createElement('div');
      propDiv.className = 'prop';
      propDiv.textContent = key + ': ' + props[key];
      info.appendChild(propDiv);
    });
  });

  document.getElementById('search').addEventListener('input', function(e) {
    var query = e.target.value.toLowerCase();
    cy.nodes().forEach(function(n) {
      var label = n.data('label').toLowerCase();
      var matches = !query || label.indexOf(query) >= 0;
      n.style('display', matches ? 'element' : 'none');
    });
  });

  filtersDiv.addEventListener('change', function(e) {
    if (e.target.type === 'checkbox') {
      var type = e.target.dataset.type;
      var visible = e.target.checked;
      cy.nodes('[type="' + type + '"]').forEach(function(n) {
        n.style('display', visible ? 'element' : 'none');
      });
    }
  });
})();
