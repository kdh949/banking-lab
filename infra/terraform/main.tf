terraform {
  required_version = ">= 1.7.0"
}

variable "namespace" {
  type    = string
  default = "banking-lab"
}

output "namespace" {
  value = var.namespace
}
