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

  var data = {
    nodes: graphData.nodes.map(function(n) {
      return {
        id: n.id,
        label: n.properties.name || n.id,
        type: n.type,
        properties: n.properties,
        style: {
          fill: NODE_COLORS[n.type] || '#999',
          stroke: '#fff',
          lineWidth: 2
        }
      };
    }),
    edges: graphData.edges.map(function(e, idx) {
      return {
        id: 'edge-' + idx,
        source: e.source,
        target: e.target,
        label: e.type,
        style: {
          stroke: '#555',
          endArrow: true
        }
      };
    })
  };

  var container = document.getElementById('graph-container');
  var width = container.scrollWidth;
  var height = container.scrollHeight || 800;

  var graph = new G6.Graph({
    container: 'graph-container',
    width: width,
    height: height,
    layout: {
      type: 'force',
      preventOverlap: true,
      linkDistance: 120,
      nodeStrength: -800,
      edgeStrength: 0.3
    },
    defaultNode: {
      size: 40,
      labelCfg: {
        style: {
          fill: '#e0e0e0',
          fontSize: 10
        }
      }
    },
    defaultEdge: {
      labelCfg: {
        autoRotate: true,
        style: {
          fill: '#888',
          fontSize: 8
        }
      }
    },
    modes: {
      default: ['drag-canvas', 'zoom-canvas', 'drag-node']
    }
  });

  graph.data(data);
  graph.render();

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

  graph.on('node:click', function(e) {
    var nodeData = e.item.getModel();
    var info = document.getElementById('info');
    info.style.display = 'block';
    info.textContent = '';

    var h4 = document.createElement('h4');
    h4.textContent = nodeData.label;
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
  });

  document.getElementById('search').addEventListener('input', function(e) {
    var query = e.target.value.toLowerCase();
    var nodes = graph.getNodes();
    nodes.forEach(function(node) {
      var model = node.getModel();
      var matches = !query || model.label.toLowerCase().indexOf(query) >= 0;
      graph.setItemState(node, 'hidden', !matches);
      if (matches) {
        graph.showItem(node);
      } else {
        graph.hideItem(node);
      }
    });
  });

  filtersDiv.addEventListener('change', function(e) {
    if (e.target.type === 'checkbox') {
      var type = e.target.dataset.type;
      var visible = e.target.checked;
      var nodes = graph.getNodes();
      nodes.forEach(function(node) {
        var model = node.getModel();
        if (model.type === type) {
          if (visible) {
            graph.showItem(node);
          } else {
            graph.hideItem(node);
          }
        }
      });
    }
  });
})();
