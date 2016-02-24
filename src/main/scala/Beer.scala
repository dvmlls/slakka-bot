object Beer {
  val Question = """(?i)(.*is.*it.*(?:beer|booze).*o'?clock.*)""".r
  val Answer = """(?i)(.*(?:it's|its|it is).*(?:beer|booze).*o'?clock.*)""".r
}
