# clake

[![CircleCI](https://circleci.com/gh/ComputeSoftware/clake.svg?style=svg)](https://circleci.com/gh/ComputeSoftware/clake)

Clojure make.

## Installation

```bash
npm install -g clake-cli
```

## Basic Usage

```bash
clake nrepl # Launch a nREPL

clake uberjar # package the project and dependencies as standalone jar

clake project-clj # Generate a project.clj file that uses lein-tools-deps

clake test # run all tests in the project
```


## Configuration

Clake's configuration is stored in `clake.edn` which should be located at the 
root of your project's directory. 


## TODO

- Write moar specs
- CLI opts Spec support
- Tasks need to be able to register a cleanup function called on exit. Task functions
should probably have a way of communicating data to the cleanup function.
- Help menu for sub-commands
- Ability to list all tasks registered
- Validate config with Spec
- when dynamic deps are added to tools-deps we can have task level dependencies
- use uber-shade on clake deps so they are "hidden" on the classpath (or look into classpath isolation)
- use color in console printing

### Tasks

- build a thin jar
- install a jar into local m2
- push to a remote repo
- watch filesystem
- show out of date deps
- create new project
- update clake (do we need this or just use NPM?)