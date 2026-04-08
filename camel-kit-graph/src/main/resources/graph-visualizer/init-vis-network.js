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

  var nodes = new vis.DataSet(graphData.nodes.map(function(n) {
    return {
      id: n.id,
      label: n.properties.name || n.id,
      color: NODE_COLORS[n.type] || '#999',
      type: n.type,
      properties: n.properties,
      font: { color: '#fff' }
    };
  }));

  var edges = new vis.DataSet(graphData.edges.map(function(e) {
    return {
      from: e.source,
      to: e.target,
      arrows: 'to',
      color: '#555',
      label: e.type,
      font: { size: 10, color: '#888', align: 'middle' }
    };
  }));

  var container = document.getElementById('graph-container');
  var data = { nodes: nodes, edges: edges };
  var options = {
    physics: {
      enabled: true,
      solver: 'forceAtlas2Based',
      forceAtlas2Based: {
        gravitationalConstant: -50,
        centralGravity: 0.01,
        springLength: 150,
        springConstant: 0.08
      },
      stabilization: { iterations: 100 }
    },
    interaction: {
      hover: true,
      tooltipDelay: 200
    }
  };

  var network = new vis.Network(container, data, options);

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

  network.on('click', function(params) {
    if (params.nodes.length > 0) {
      var nodeId = params.nodes[0];
      var nodeData = graphData.nodes.find(function(n) { return n.id === nodeId; });
      if (!nodeData) return;

      var info = document.getElementById('info');
      info.style.display = 'block';
      info.textContent = '';

      var h4 = document.createElement('h4');
      h4.textContent = nodeData.properties.name || nodeId;
      info.appendChild(h4);

      var typeProp = document.createElement('div');
      typeProp.className = 'prop';
      typeProp.textContent = 'Type: ' + nodeData.type;
      info.appendChild(typeProp);

      Object.keys(nodeData.properties).forEach(function(key) {
        var propDiv = document.createElement('div');
        propDiv.className = 'prop';
        propDiv.textContent = key + ': ' + nodeData.properties[key];
        info.appendChild(propDiv);
      });
    }
  });

  document.getElementById('search').addEventListener('input', function(e) {
    var query = e.target.value.toLowerCase();
    var allNodes = nodes.get();
    allNodes.forEach(function(node) {
      var matches = !query || node.label.toLowerCase().indexOf(query) >= 0;
      nodes.update({ id: node.id, hidden: !matches });
    });
  });

  filtersDiv.addEventListener('change', function(e) {
    if (e.target.type === 'checkbox') {
      var type = e.target.dataset.type;
      var visible = e.target.checked;
      var allNodes = nodes.get();
      allNodes.forEach(function(node) {
        if (node.type === type) {
          nodes.update({ id: node.id, hidden: !visible });
        }
      });
    }
  });
})();
