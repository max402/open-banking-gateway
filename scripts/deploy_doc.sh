#!/usr/bin/env bash

if [[ $TRAVIS_REPO_SLUG != "max402/open-banking-gateway"
    || $TRAVIS_JDK_VERSION != "openjdk8"
    || $TRAVIS_PULL_REQUEST != "false" ]];
then
  echo "ERROR: Documentation deployment for this build not allowed"
  exit 1
fi

if [ ! "$TRAVIS_TAG" ];
then
  TRAVIS_TAG="develop"
fi

echo -e "Publishing Documentation...\n"


if [ "$TRAVIS_TAG" == develop ];
then
  echo "deploy from develop. TRAVIS_TAG=$TRAVIS_TAG"
else
(
  echo "deploy by tag. TRAVIS_TAG=$TRAVIS_TAG"
)
fi


echo -e "Published Documentation to gh-pages.\n"
