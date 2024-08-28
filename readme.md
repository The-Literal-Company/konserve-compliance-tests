### [additional compliance tests](https://github.com/replikativ/konserve/tree/main/test/konserve) for konserve implementors as a dependency

```clojure
 com.literalco/konserve-compliance-tests {:git/url "https://github.com/The-Literal-Company/konserve-compliance-tests.git"
                                          :git/sha "4e7210bc42d5cfc0738ab13d1e71ee07b50fd288"}
```

The tests are split between .clj and .cljs files, but are aspirationally identical. You can exclude clojurescript and related transitive deps with using:

```clojure
 com.literalco/konserve-compliance-tests {:git/url "https://github.com/The-Literal-Company/konserve-compliance-tests.git"
                                          :git/sha "4e7210bc42d5cfc0738ab13d1e71ee07b50fd288"
                                          :exclusions [com.github.pkpkpk/cljs-node-io
                                                       com.github.pkpkpk/fress
                                                       fress/fress
                                                       org.clojure/clojurescript
                                                       org.clojars.mmb90/cljs-cache]}
```
