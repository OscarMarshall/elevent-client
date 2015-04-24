# Elevent Client

The web front-end for Elevent Solutions, an all-around event software solution.

## Dependencies

* [Leiningen](http://leiningen.org/) - download script from website and follow directions to install.
* [Bower](http://bower.io/) - install with `$ npm install -g bower`.

## Usage

1. `$ bower install`.
2. `$ lein figwheel`.
3. Optionally for repl support, in another terminal `$ lein repl` then
   `elevent-client.dev=> (browser-repl)`.
4. In a browser, navigate to [localhost:8000](http://localhost:8000/).

## Production

	$ lein clean
	$ lein cljsbuild once prod
The compiled files will be in `./resources/public/`

## Platforms

The build system runs on anything that has a JRE.
Our web app will run in most browsers, but we only tests in Chrome.

## Authors

Oscar Marshall and Leslie Baker
