package com.uptech.windalerts

import scala.xml._

object MS extends App {
  (XML.load("pom.xml") \\ "dependencies") \ "dependency" foreach ((dependency: Node) => {
    val groupId = (dependency \ "groupId").text
    val artifactId = (dependency \ "artifactId").text
    val version = (dependency \ "version").text
    val scope = (dependency \ "scope").text
    val classifier = (dependency \ "classifier").text
    val artifactValName: String = artifactId.replaceAll("[-\\.]", "_")

    print(" \"%s\" %%%% \"%s\" %%%% \"%s\",".format(artifactValName, groupId, artifactId, version))
    scope match {
      case "" => print("\n")
      case _ => print(" %% \"%s\"\n".format(scope))
    }
    None
  });
}