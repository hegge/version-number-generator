# version-number-generator

[![Build Status](https://travis-ci.org/hegge/version-number-generator.svg?branch=master)](https://travis-ci.org/hegge/version-number-generator)

A Clojure app that generates version numbers, which can easily be deployed to Heroku.

Runs on https://version-number-generator.herokuapp.com/

Based on [Getting Started with Clojure](https://devcenter.heroku.com/articles/getting-started-with-clojure)

## Running Locally

Make sure you have Clojure installed.  Also, install the [Heroku Toolbelt](https://toolbelt.heroku.com/).

```sh
$ git clone https://github.com/hegge/version-number-generator.git
$ cd version-number-generator
$ source postgres_env.sh
$ lein repl
user=> (require 'version-number-generator.web)
user=>(def server (version-number-generator.web/-main))
```

Your app should now be running on [localhost:5000](http://localhost:5000/).

## Deploying to Heroku

```sh
$ heroku create
$ git push heroku master
$ heroku open
```

or

[![Deploy to Heroku](https://www.herokucdn.com/deploy/button.png)](https://heroku.com/deploy)

## Documentation

For more information about using Clojure on Heroku, see these Dev Center articles:

- [Clojure on Heroku](https://devcenter.heroku.com/categories/clojure)
