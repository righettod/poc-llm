import flask
import re

app = flask.Flask(__name__)


@app.route('/realOne00', methods=['GET'])
def realOne00():
    # Real reflected XSS
    my_param1 = flask.request.args.get('my_param')
    content = f"<html><body>Hello {my_param1}</body></html>"
    return content


@app.route('/realOne01', methods=['GET'])
def realOne01():
    # Real reflected XSS with an ineffective sanitization
    my_param2 = flask.request.args.get('my_param')
    my_param2 = my_param2.replace("/>", "")
    content = f"<html><body>Hello {my_param2}</body></html>"
    return content


@app.route('/fakeOne00', methods=['GET'])
def fakeOne00():
    # Fake reflected XSS
    my_param3 = flask.request.args.get('my_param')
    my_param3 = re.sub(r'[<>\'"]+', '', my_param3)
    content = f"<html><body>Hello {my_param3}</body></html>"
    return content


@app.route('/fakeOne01', methods=['GET'])
def fakeOne01():
    # Fake reflected XSS
    my_param4 = flask.request.args.get('my_param')
    if len(re.findall(r'^[a-z]+$', my_param4)) == 1:
        content = f"<html><body>Hello {my_param4}</body></html>"
    else:
        content = "<html><body>NONE</body></html>"
    return content


@app.route('/fakeOne02', methods=['GET'])
def fakeOne02():
    # Fake reflected XSS as it is not
    # possible to inject a new tag in the html body.
    my_param5 = flask.request.args.get('my_param')
    my_param5 = my_param5.replace("<", "").replace(">", "")
    content = f"<html><body>Hello {my_param5}</body></html>"
    return content


if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=True)
