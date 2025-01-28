require QuickStruct

defmodule WisenWeb.Items do

  use WisenWeb, :live_view

  QuickStruct.define_module(Input, [:label])

  def inp(label) do
    Input.make(label)
  end

  QuickStruct.define_module(Fragment, [:items])

  def fragment(items) do
    Fragment.make(items)
  end

  QuickStruct.define_module(Map, [:function, :item])

  def map(f, items) do
    Map.make(f, items)
  end

  QuickStruct.define_module(Focus, [:lens, :item])

  def focus(lens, item) do
    Focus.make(lens, item)
  end

  def run(item, value, on_change) do
    assigns = %{value: value}
    case item do
      %Input{label: label} ->
        ~H"""
        <.form
          phx-change={on_change}>
          <.input
            type="text"
            name="TODO Name"
            label={label}
            value={@value}
          />
        </.form>
        """
      %Fragment{items: items} -> :todo
      %Map{function: f, item: item} -> :todo
      %Focus{lens: l, item: item} -> :todo
    end
  end

end
