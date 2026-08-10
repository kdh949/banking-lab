import { customerBff } from "../../../server/bff";

export const GET = customerBff.proxy.GET;
export const POST = customerBff.proxy.POST;
export const PUT = customerBff.proxy.PUT;
export const PATCH = customerBff.proxy.PATCH;
export const DELETE = customerBff.proxy.DELETE;
