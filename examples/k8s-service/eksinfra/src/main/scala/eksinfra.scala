import besom.*
import besom.api.kubernetes
import besom.api.eks // Why `eks` and not `aws.eks` ???
import besom.api.awsx

@main def main = Pulumi.run:
  val namespaceName = "my-application"

  val vpc = awsx.ec2.Vpc("my-vpc", awsx.ec2.VpcArgs(cidrBlock = Some("10.0.0.0/16")))

  // Create an EKS cluster using the default VPC and subnet
  val cluster = eks.Cluster(
    namespaceName,
    eks.ClusterArgs(
      vpcId = vpc.vpcId,
      subnetIds = vpc.publicSubnetIds,
      instanceType = "t3.medium",
      desiredCapacity = 2,
      minSize = 1,
      maxSize = 3,
      storageClasses = Some("gp2")
    )
  )

  Stack(cluster).exports(
    kubeconfig = cluster.kubeconfig,
  )
