# Elevent Client

The web front-end for Elevent Solutions, an all-around event software solution.

## Dependencies

* [Leiningen](http://leiningen.org/)
* [Bower](http://bower.io/)

## Usage

1. `$ bower install`.
2. `$ lein figwheel`.
3. Optionally for repl support, in another terminal `$ lein repl` then
   `elevent-client.dev=> (browser-repl)`.
4. In a browser, navigate to [localhost:8000](http://localhost:8000/).

## Production

    $ lein cljsbuild clean
    $ lein ring uberjar
