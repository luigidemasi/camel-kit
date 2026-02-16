import sys, yaml, jsonschema, urllib.request, json

schema = json.loads(urllib.request.urlopen(sys.argv[1]).read())
data = yaml.safe_load(open(sys.argv[2]))
jsonschema.validate(instance=data, schema=schema)
print("✅ Validation successful!")
