defmodule WisenWeb.PageController do
  use WisenWeb, :controller

  def home(conn, _params) do
    # The home page is often custom made,
    # so skip the default app layout.
    render(conn, :home, layout: false)
  end

  def other(conn, _params) do
    render(conn, :other, layout: false)
  end
end
