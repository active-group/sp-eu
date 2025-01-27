defmodule Wisen.Repo do
  use Ecto.Repo,
    otp_app: :wisen,
    adapter: Ecto.Adapters.SQLite3
end
