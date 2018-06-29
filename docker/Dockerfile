FROM circleci/clojure:tools-deps-node

ARG shoe_version

RUN sudo npm install shoe-cli@$shoe_version -g

RUN shoe -v
RUN shoe test