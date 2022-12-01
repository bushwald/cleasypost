# Post Easy

Clojure client for the [EasyPost](https://easypost.com) web service

very much a WIP

## Usage

``` clojure
(def origin-adr
  {:address
   {:name "Steve Brule"
    :street1 "1849 Geary Blvd"
    :street2 "Apt C"
    :city "San Francisco"
    :state "NY"
    :zip "94115"
    :country "US"
    :phone "4155555555"}})

(def destination-adr
  {:address
   {:name "bushwald"
    :street1 "16 Arcade"
    :city "Nashville"
    :state "TN"
    :zip "37219"
    :country "US"
    :phone "7185555555"}
   :verify true})

(def prcl
  {:parcel
   {:length 4
    :width 4
    :height 6.2
    :weight 32}})

(def shpmnt {:to_address (:address origin-adr)
             :from_address (:address destination-adr)
             :parcel (:parcel prcl)})

(def token "EZ299nnTK2ffkdsefjlsiejfake_tokenR1piAarlSa6vA234RsA")

(def ep-shpmnt (create-shipment {:shipment shpmnt} token))

(def one-call-buy-shpmnt
  {:shipment (merge shpmnt
                    {:service "Priority"
                     :carrier_accounts ["ca_e61a814d26testa90b663a5zz053d18"]})})

(:postage_label (buy-shipment one-call-buy-shpmnt token))

(:postage_label (buy-shipment {:rate (get-lowest-rate (:rates ep-shpmnt))} token))
```

FIXME: write usage documentation!

Run the project's tests (they'll fail until you edit them):

    $ clojure -T:build test

Run the project's CI pipeline and build a JAR (this will fail until you edit the tests to pass):

    $ clojure -T:build ci

This will produce an updated `pom.xml` file with synchronized dependencies inside the `META-INF`
directory inside `target/classes` and the JAR in `target`. You can update the version (and SCM tag)
information in generated `pom.xml` by updating `build.clj`.

Install it locally (requires the `ci` task be run first):

    $ clojure -T:build install


I used [CLJ-New](https://github.com/seancorfield/clj-new) to generate the basic
project scaffold.  Check it out.

## License

Copyright © 2022 Bradley Allen

Distributed under the Eclipse Public License version 1.0.
