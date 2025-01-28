require QuickStruct

defmodule Wisen.Domain do

  # Properties

  # which is on of :elderly, :queer, :immigrant
  QuickStruct.define_module(CatersToProperty, [:which])

  QuickStruct.define_module(PlacedAtProperty, [:longitude, :latitude])

  # which is one of :social, :education, :culture, :food, ...
  QuickStruct.define_module(AddressesNeedProperty, [:which])

  # QuickStruct.define_module(Event, [:title, :when, :where])

  # QuickStruct.define_module(HostsEventProperty, [event: Event.t])

  QuickStruct.define_module(HasGoogleMapsProperty, [uri: String.t])

  QuickStruct.define_module(HasWebsiteProperty, [uri: String.t])

  # Resource

  QuickStruct.define_module(Resource, [:title, :properties])

  def set_title(resource, title) do
    %Resource{resource | title: title}
  end

  def add_empty_caters_to(resource) do
    prop = CatersToProperty.make(:elderly)
    %Resource{resource | properties: resource.properties ++ [prop]}
  end

end
