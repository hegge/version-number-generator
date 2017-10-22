#!/bin/sh

curl "https://version-number-generator.herokuapp.com/v1/version?major=3&minor=0&branch=master&commit=deadbeef" --header "authentication:"

curl "https://version-number-generator.herokuapp.com/v1/version?major=3&minor=0&branch=maint-3.0.1400&commit=deadbeef2" --header "authentication:"

curl "https://version-number-generator.herokuapp.com/v1/version?major=3&minor=0&branch=torstein/break-stuff&commit=deadbeef3" --header "authentication:"

curl https://version-number-generator.herokuapp.com/versions
