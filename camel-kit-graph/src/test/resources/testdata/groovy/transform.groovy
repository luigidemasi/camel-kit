// Inline script referenced by routes
def body = request.body
result = body.replaceAll("old", "new")
