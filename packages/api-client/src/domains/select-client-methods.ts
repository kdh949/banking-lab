export function selectClientMethods<
  TClient extends object,
  const TKeys extends readonly (keyof TClient)[]
>(client: TClient, keys: TKeys): Pick<TClient, TKeys[number]> {
  return Object.fromEntries(keys.map((key) => [key, client[key]])) as Pick<TClient, TKeys[number]>;
}
